package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.payload.WatchHistoryDTO;
import java.util.List;

public interface WatchHistoryService {

    // Added episodeId to the signature
    void updateProgress(Long profileId, Long contentId, Long episodeId, int watchedSeconds, int totalDurationSeconds);

    List<WatchHistoryDTO> getActiveWatchHistory(Long profileId);
}