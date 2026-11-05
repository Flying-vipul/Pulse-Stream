package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.payload.HeartbeatRequest;
import com.netflix.streaming.platform.payload.WatchHistoryDTO;
import com.netflix.streaming.platform.service.WatchHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Watch history endpoints.
 *
 * <p>SECURITY: All endpoints inject {@link Authentication} from Spring Security''s
 * SecurityContext (populated by the JWT filter). No userId is accepted
 * from the client URL — doing so creates an IDOR vulnerability.
 * Profile ownership is verified in the service layer.
 *
 * <p>New routes:
 * <pre>
 *   POST /api/v1/profiles/{profileId}/history/heartbeat
 *   GET  /api/v1/profiles/{profileId}/history/continue-watching
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/history")
public class WatchHistoryController {

    @Autowired
    private WatchHistoryService watchHistoryService;

    /**
     * Records or updates watch progress.
     * POST /api/v1/profiles/{profileId}/history/heartbeat
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<String> saveProgress(
            Authentication authentication,
            @PathVariable Long profileId,
            @RequestBody HeartbeatRequest request) {

        watchHistoryService.updateProgress(authentication, profileId, request);
        return ResponseEntity.ok("Progress saved.");
    }

    /**
     * Returns incomplete watch history ordered by most recently watched.
     * GET /api/v1/profiles/{profileId}/history/continue-watching
     */
    @GetMapping("/continue-watching")
    public ResponseEntity<List<WatchHistoryDTO>> getContinueWatching(
            Authentication authentication,
            @PathVariable Long profileId) {
            
        List<WatchHistoryDTO> history = watchHistoryService.getActiveWatchHistory(authentication, profileId);
        return ResponseEntity.ok(history);
    }
}

