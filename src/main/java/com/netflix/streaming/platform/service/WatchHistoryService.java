package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.payload.WatchHistoryDTO;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface WatchHistoryService {

    /**
     * Records or updates watch progress for the authenticated user''s first profile.
     * The user identity is derived from the JWT (never from a client-supplied userId).
     *
     * @param auth              the current authentication (from JWT)
     * @param contentId         the content being watched
     * @param episodeId         the episode being watched; null for movies
     * @param watchedSeconds    seconds watched so far (must be >= 0)
     * @param totalDurationSeconds total content duration in seconds (must be > 0)
     */
    void updateProgress(Authentication auth, Long contentId, Long episodeId,
                        int watchedSeconds, int totalDurationSeconds);

    /**
     * Returns incomplete watch history for the authenticated user''s first profile,
     * ordered by most recently watched.
     *
     * @param auth the current authentication (from JWT)
     */
    List<WatchHistoryDTO> getActiveWatchHistory(Authentication auth);
}
