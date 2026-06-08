package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.Episode;
import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.WatchHistory;
import com.netflix.streaming.platform.payload.WatchHistoryDTO;
import com.netflix.streaming.platform.repositories.ContentRepository;
import com.netflix.streaming.platform.repositories.EpisodeRepository;
import com.netflix.streaming.platform.repositories.ProfileRepository;
import com.netflix.streaming.platform.repositories.WatchHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WatchHistoryServiceImpl implements WatchHistoryService {

    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ContentRepository contentRepository;
    @Autowired private EpisodeRepository episodeRepository;

    @Override
    public void updateProgress(Long profileId, Long contentId, Long episodeId, int watchedSeconds, int totalDurationSeconds) {

        // 1. Fetch the actual Entity Objects from the Database
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", profileId));

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Content", "id", contentId));

        // 2. Handle the Nullable Episode (If it's a Movie, this stays null)
        Episode episode = null;
        if (episodeId != null) {
            episode = episodeRepository.findById(episodeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Episode", "id", episodeId));
        }

        // 3. Find the existing history record, or create a brand new one
        Optional<WatchHistory> existingHistory = watchHistoryRepository
                .findByProfileAndContentAndEpisode(profile, content, episode);

        WatchHistory history = existingHistory.orElseGet(WatchHistory::new);

        // 4. If it's new, set the objects
        if (history.getId() == null) {
            history.setProfile(profile);
            history.setContent(content);
            history.setEpisode(episode);
        }

        // 5. Update the progress using your NEW entity column names!
        history.setStoppedAtSeconds(watchedSeconds);

        // The 90% Rule
        double percentageWatched = (double) watchedSeconds / totalDurationSeconds;
        history.setIsCompleted(percentageWatched >= 0.90);

        // 6. Save to PostgreSQL
        watchHistoryRepository.save(history);
    }

    @Override
    public List<WatchHistoryDTO> getActiveWatchHistory(Long profileId) {

        // 1. Fetch the Profile Object first
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", profileId));

        // 2. Fetch incomplete history, most recent first
        List<WatchHistory> historyList = watchHistoryRepository
                .findByProfileAndIsCompletedFalseOrderByLastWatchedAtDesc(profile);

        // 3. Map to enriched DTO — title, thumbnail, episode info included
        //    so the frontend can render Continue Watching cards in ONE call
        return historyList.stream().map(history -> {
            WatchHistoryDTO dto = new WatchHistoryDTO();

            // Core watch data
            dto.setContentId(history.getContent().getId());
            dto.setLastWatchedSeconds(history.getStoppedAtSeconds());

            // Content metadata for the card
            dto.setTitle(history.getContent().getTitle());
            dto.setThumbnailUrl(history.getContent().getThumbnailUrl());

            // Duration for progress bar (durationMinutes x 60)
            if (history.getContent().getDurationMinutes() != null) {
                dto.setTotalDurationSeconds(history.getContent().getDurationMinutes() * 60);
            }

            // Episode data (null for movies, populated for TV shows)
            if (history.getEpisode() != null) {
                dto.setEpisodeId(history.getEpisode().getId());
                dto.setEpisodeTitle(history.getEpisode().getTitle());
            }

            return dto;
        }).toList();
    }
}