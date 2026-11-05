package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.payload.HeartbeatRequest;
import com.netflix.streaming.platform.payload.WatchHistoryDTO;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface WatchHistoryService {

    /**
     * Records or updates watch progress for a specific profile.
     * The profile must belong to the authenticated user.
     *
     * @param auth        the current authentication (from JWT)
     * @param profileId   the ID of the profile watching the content
     * @param request     the heartbeat request containing position info
     */
    void updateProgress(Authentication auth, Long profileId, HeartbeatRequest request);

    /**
     * Returns incomplete watch history for the specified profile,
     * ordered by most recently watched.
     *
     * @param auth      the current authentication (from JWT)
     * @param profileId the ID of the profile
     */
    List<WatchHistoryDTO> getActiveWatchHistory(Authentication auth, Long profileId);
}

