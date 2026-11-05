package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.Episode;
import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.WatchHistory;
import com.netflix.streaming.platform.payload.HeartbeatRequest;
import com.netflix.streaming.platform.payload.WatchHistoryDTO;
import com.netflix.streaming.platform.repositories.ContentRepository;
import com.netflix.streaming.platform.repositories.EpisodeRepository;
import com.netflix.streaming.platform.repositories.WatchHistoryRepository;
import com.netflix.streaming.platform.security.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WatchHistoryServiceImpl implements WatchHistoryService {

    private static final double COMPLETION_THRESHOLD = 0.90;

    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired private ContentRepository contentRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private AuthUtil authUtil;


    // UPDATE PROGRESS (UPSERT)

    @Override
    @Transactional
    public void updateProgress(Authentication auth, Long profileId, HeartbeatRequest request) {

        int watchedSeconds = request.getPositionSeconds();

        // --- Input validation ---
        if (watchedSeconds < 0) {
            throw new APIException("positionSeconds must be >= 0.");
        }

        // --- Ownership check ---
        Profile profile = authUtil.requireOwnedProfile(profileId, auth);

        Content content = contentRepository.findById(request.getContentId())
                .orElseThrow(() -> new ResourceNotFoundException("Content", "id", request.getContentId()));

        Episode episode = null;
        int durationSeconds = 0;

        if (request.getEpisodeId() != null) {
            episode = episodeRepository.findById(request.getEpisodeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Episode", "id", request.getEpisodeId()));
            
            // Ensure episode belongs to the content
            if (!episode.getSeason().getContent().getId().equals(content.getId())) {
                throw new APIException("Episode does not belong to the specified content.");
            }
            if (episode.getDurationMinutes() != null) {
                durationSeconds = episode.getDurationMinutes() * 60;
            }
        } else {
            if (content.getDurationMinutes() != null) {
                durationSeconds = content.getDurationMinutes() * 60;
            }
        }

        if (durationSeconds <= 0) {
            throw new APIException("Authoritative duration is invalid or not set in the database.");
        }

        if (watchedSeconds > durationSeconds) {
            watchedSeconds = durationSeconds; // Clamp position to the valid duration length
        }

        // --- Upsert: find existing or create new ---
        Optional<WatchHistory> existing = watchHistoryRepository
                .findByProfileAndContentAndEpisode(profile, content, episode);

        WatchHistory history = existing.orElseGet(WatchHistory::new);

        if (history.getId() == null) {
            history.setProfile(profile);
            history.setContent(content);
            history.setEpisode(episode);
        }

        history.setStoppedAtSeconds(watchedSeconds);

        double pct = (double) watchedSeconds / durationSeconds;
        history.setCompleted(pct >= COMPLETION_THRESHOLD);

        watchHistoryRepository.save(history);
    }


    // GET ACTIVE WATCH HISTORY ("Continue Watching")

    @Override
    @Transactional(readOnly = true)
    public List<WatchHistoryDTO> getActiveWatchHistory(Authentication auth, Long profileId) {
        // Ownership: verify requested profile belongs to authenticated user
        Profile profile = authUtil.requireOwnedProfile(profileId, auth);

        // The @EntityGraph on this repository method JOIN FETCHes content and episode
        // in a single SQL query — no N+1 queries here.
        List<WatchHistory> historyList = watchHistoryRepository
                .findByProfileAndIsCompletedFalseOrderByLastWatchedAtDesc(profile);

        return historyList.stream().map(this::toDTO).toList();
    }


    // DTO MAPPING

    private WatchHistoryDTO toDTO(WatchHistory history) {
        WatchHistoryDTO dto = new WatchHistoryDTO();
        dto.setContentId(history.getContent().getId());
        dto.setCurrentPositionSeconds(history.getStoppedAtSeconds());
        dto.setTitle(history.getContent().getTitle());
        dto.setThumbnailUrl(history.getContent().getThumbnailUrl());
        dto.setCompleted(history.isCompleted());
        dto.setLastWatchedAt(history.getLastWatchedAt());

        int durationSeconds = 0;

        // Episode fields are populated for TV episodes
        if (history.getEpisode() != null) {
            dto.setEpisodeId(history.getEpisode().getId());
            dto.setEpisodeTitle(history.getEpisode().getTitle());
            dto.setSeasonNumber(history.getEpisode().getSeason().getSeasonNumber());
            dto.setEpisodeNumber(history.getEpisode().getEpisodeNumber());
            if (history.getEpisode().getDurationMinutes() != null) {
                durationSeconds = history.getEpisode().getDurationMinutes() * 60;
            }
        } else {
            if (history.getContent().getDurationMinutes() != null) {
                durationSeconds = history.getContent().getDurationMinutes() * 60;
            }
        }

        dto.setDurationSeconds(durationSeconds);
        if (durationSeconds > 0) {
            dto.setProgressPercentage(((double) history.getStoppedAtSeconds() / durationSeconds) * 100);
        }

        return dto;
    }
}
