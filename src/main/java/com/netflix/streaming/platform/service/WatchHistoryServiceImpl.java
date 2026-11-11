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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            if (episode.getDurationMinutes() != null && episode.getDurationMinutes() > 0) {
                durationSeconds = episode.getDurationMinutes() * 60;
            }
        } else {
            if (content.getDurationMinutes() != null && content.getDurationMinutes() > 0) {
                durationSeconds = content.getDurationMinutes() * 60;
            }
        }

        // Fallback 1: Use duration reported by client/player if database value was missing or 0
        if (durationSeconds <= 0 && request.getDurationSeconds() != null && request.getDurationSeconds() > 0) {
            durationSeconds = request.getDurationSeconds();
            // Auto-heal DB duration if not set
            if (episode != null && (episode.getDurationMinutes() == null || episode.getDurationMinutes() <= 0)) {
                episode.setDurationMinutes(Math.max(1, durationSeconds / 60));
                episodeRepository.save(episode);
            } else if (episode == null && (content.getDurationMinutes() == null || content.getDurationMinutes() <= 0)) {
                content.setDurationMinutes(Math.max(1, durationSeconds / 60));
                contentRepository.save(content);
            }
        }

        // Fallback 2: Safe default if database has no duration and player didn't report
        if (durationSeconds <= 0) {
            durationSeconds = Math.max(watchedSeconds + 60, 3600); // 1-hour safe baseline
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

        // Fetch uncompleted watch records ordered by most recent
        List<WatchHistory> historyList = watchHistoryRepository
                .findByProfileAndIsCompletedFalseOrderByLastWatchedAtDesc(profile);

        // Deduplicate by contentId so only the most recent episode of a series appears,
        // and filter out accidental clicks (< 5 seconds watched)
        Map<Long, WatchHistory> latestByContent = new LinkedHashMap<>();
        for (WatchHistory wh : historyList) {
            if (wh.getStoppedAtSeconds() >= 5) {
                latestByContent.putIfAbsent(wh.getContent().getId(), wh);
            }
        }

        return latestByContent.values().stream().map(this::toDTO).toList();
    }


    // GET PROGRESS FOR SPECIFIC CONTENT / EPISODE

    @Override
    @Transactional(readOnly = true)
    public WatchHistoryDTO getProgressForContent(Authentication auth, Long profileId, Long contentId, Long episodeId) {
        Profile profile = authUtil.requireOwnedProfile(profileId, auth);
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Content", "id", contentId));

        Episode episode = null;
        if (episodeId != null) {
            episode = episodeRepository.findById(episodeId).orElse(null);
        }

        Optional<WatchHistory> historyOpt = watchHistoryRepository
                .findByProfileAndContentAndEpisode(profile, content, episode);

        if (historyOpt.isEmpty() && episodeId == null) {
            List<WatchHistory> list = watchHistoryRepository
                    .findByProfileAndContentOrderByLastWatchedAtDesc(profile, content);
            if (!list.isEmpty()) {
                historyOpt = Optional.of(list.get(0));
            }
        }

        return historyOpt.map(this::toDTO).orElse(null);
    }


    // REMOVE FROM CONTINUE WATCHING

    @Override
    @Transactional
    public void removeFromWatchHistory(Authentication auth, Long profileId, Long contentId) {
        Profile profile = authUtil.requireOwnedProfile(profileId, auth);
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Content", "id", contentId));

        watchHistoryRepository.deleteByProfileAndContent(profile, content);
    }


    // DTO MAPPING

    private WatchHistoryDTO toDTO(WatchHistory history) {
        WatchHistoryDTO dto = new WatchHistoryDTO();
        dto.setContentId(history.getContent().getId());
        dto.setCurrentPositionSeconds(history.getStoppedAtSeconds());
        dto.setTitle(history.getContent().getTitle());
        dto.setThumbnailUrl(history.getContent().getThumbnailUrl());
        dto.setBannerUrl(history.getContent().getBannerUrl());
        dto.setCompleted(history.isCompleted());
        dto.setLastWatchedAt(history.getLastWatchedAt());

        int durationSeconds = 0;

        // Episode fields are populated for TV episodes
        if (history.getEpisode() != null) {
            dto.setEpisodeId(history.getEpisode().getId());
            dto.setEpisodeTitle(history.getEpisode().getTitle());
            if (history.getEpisode().getSeason() != null) {
                dto.setSeasonNumber(history.getEpisode().getSeason().getSeasonNumber());
            }
            dto.setEpisodeNumber(history.getEpisode().getEpisodeNumber());
            if (history.getEpisode().getDurationMinutes() != null && history.getEpisode().getDurationMinutes() > 0) {
                durationSeconds = history.getEpisode().getDurationMinutes() * 60;
            }
        } else {
            if (history.getContent().getDurationMinutes() != null && history.getContent().getDurationMinutes() > 0) {
                durationSeconds = history.getContent().getDurationMinutes() * 60;
            }
        }

        dto.setDurationSeconds(durationSeconds);
        if (durationSeconds > 0) {
            double pct = ((double) history.getStoppedAtSeconds() / durationSeconds) * 100.0;
            dto.setProgressPercentage(Math.min(100.0, Math.max(0.0, pct)));
        } else {
            dto.setProgressPercentage(0.0);
        }

        return dto;
    }
}
