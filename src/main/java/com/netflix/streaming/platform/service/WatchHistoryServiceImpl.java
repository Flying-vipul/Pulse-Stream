package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.Episode;
import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.model.WatchHistory;
import com.netflix.streaming.platform.payload.WatchHistoryDTO;
import com.netflix.streaming.platform.repositories.ContentRepository;
import com.netflix.streaming.platform.repositories.EpisodeRepository;
import com.netflix.streaming.platform.repositories.ProfileRepository;
import com.netflix.streaming.platform.repositories.UserRepository;
import com.netflix.streaming.platform.repositories.WatchHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WatchHistoryServiceImpl implements WatchHistoryService {

    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ContentRepository contentRepository;
    @Autowired private EpisodeRepository episodeRepository;

    // =========================================================================
    // OWNERSHIP RESOLUTION
    // Always derive user from JWT principal — never trust a client-supplied ID.
    // =========================================================================

    private User resolveUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new APIException("Not authenticated.");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", auth.getName()));
    }

    /**
     * Resolves the user''s first profile.
     * This mirrors the original frontend contract (user has one active profile).
     * Future work: accept profileId and verify ownership via verifyProfileOwnership().
     */
    private Profile resolveFirstProfile(Authentication auth) {
        User user = resolveUser(auth);
        List<Profile> profiles = profileRepository.findByUser(user);
        if (profiles.isEmpty()) {
            throw new ResourceNotFoundException("Profile", "userId", user.getId());
        }
        return profiles.get(0);
    }

    // =========================================================================
    // UPDATE PROGRESS (UPSERT)
    // =========================================================================

    @Override
    @Transactional
    public void updateProgress(Authentication auth, Long contentId, Long episodeId,
                               int watchedSeconds, int totalDurationSeconds) {

        // --- Input validation ---
        if (watchedSeconds < 0) {
            throw new APIException("watchedSeconds must be >= 0.");
        }
        if (totalDurationSeconds <= 0) {
            throw new APIException("totalDurationSeconds must be > 0.");
        }
        if (watchedSeconds > totalDurationSeconds) {
            throw new APIException("watchedSeconds cannot exceed totalDurationSeconds.");
        }

        // --- Ownership check: derive profile from JWT, never from URL ---
        Profile profile = resolveFirstProfile(auth);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Content", "id", contentId));

        Episode episode = null;
        if (episodeId != null) {
            episode = episodeRepository.findById(episodeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Episode", "id", episodeId));
        }

        // --- Upsert: find existing or create new ---
        // The DB unique constraint on (profile_id, content_id, episode_id) is the
        // final safety net against concurrent duplicate inserts.
        Optional<WatchHistory> existing = watchHistoryRepository
                .findByProfileAndContentAndEpisode(profile, content, episode);

        WatchHistory history = existing.orElseGet(WatchHistory::new);

        if (history.getId() == null) {
            history.setProfile(profile);
            history.setContent(content);
            history.setEpisode(episode);
        }

        history.setStoppedAtSeconds(watchedSeconds);

        // 90% completion rule — safe against totalDurationSeconds = 0 (validated above)
        double pct = (double) watchedSeconds / totalDurationSeconds;
        history.setCompleted(pct >= 0.90);

        watchHistoryRepository.save(history);
    }

    // =========================================================================
    // GET ACTIVE WATCH HISTORY ("Continue Watching")
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<WatchHistoryDTO> getActiveWatchHistory(Authentication auth) {
        // Ownership: profile derived from authenticated user''s JWT
        Profile profile = resolveFirstProfile(auth);

        // The @EntityGraph on this repository method JOIN FETCHes content and episode
        // in a single SQL query — no N+1 queries here.
        List<WatchHistory> historyList = watchHistoryRepository
                .findByProfileAndIsCompletedFalseOrderByLastWatchedAtDesc(profile);

        return historyList.stream().map(this::toDTO).toList();
    }

    // =========================================================================
    // DTO MAPPING
    // =========================================================================

    private WatchHistoryDTO toDTO(WatchHistory history) {
        WatchHistoryDTO dto = new WatchHistoryDTO();
        dto.setContentId(history.getContent().getId());
        dto.setLastWatchedSeconds(history.getStoppedAtSeconds());
        dto.setTitle(history.getContent().getTitle());
        dto.setThumbnailUrl(history.getContent().getThumbnailUrl());

        // Duration for progress bar — content stores minutes, frontend needs seconds
        if (history.getContent().getDurationMinutes() != null) {
            dto.setTotalDurationSeconds(history.getContent().getDurationMinutes() * 60);
        }

        // Episode fields are null for movies; populated for TV episodes
        if (history.getEpisode() != null) {
            dto.setEpisodeId(history.getEpisode().getId());
            dto.setEpisodeTitle(history.getEpisode().getTitle());
        }

        return dto;
    }
}
