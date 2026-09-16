package com.netflix.streaming.platform.controller;

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
 * SecurityContext (populated by the JWT filter). No userId or profileId is accepted
 * from the client URL — doing so creates an IDOR vulnerability.
 *
 * <p>Old routes (removed — IDOR risk):
 * <pre>
 *   /api/users/{userId}/history/heartbeat
 *   /api/profiles/{profileId}/history/heartbeat
 * </pre>
 *
 * <p>New routes:
 * <pre>
 *   POST /api/v1/me/history/heartbeat
 *   GET  /api/v1/me/history/continue-watching
 * </pre>
 *
 * <p>The controller is intentionally thin — validation, ownership checks,
 * and business logic live in {@link WatchHistoryService}.
 */
@RestController
@RequestMapping("/api/v1/me/history")
public class WatchHistoryController {

    @Autowired
    private WatchHistoryService watchHistoryService;

    /**
     * Records or updates watch progress.
     * POST /api/v1/me/history/heartbeat
     *
     * @param contentId            the content being watched
     * @param episodeId            the episode; null for movies
     * @param watchedSeconds       seconds watched (must be >= 0)
     * @param totalDurationSeconds total length (must be > 0 and >= watchedSeconds)
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<String> saveProgress(
            Authentication authentication,
            @RequestParam Long contentId,
            @RequestParam(required = false) Long episodeId,
            @RequestParam int watchedSeconds,
            @RequestParam int totalDurationSeconds) {

        watchHistoryService.updateProgress(
                authentication, contentId, episodeId, watchedSeconds, totalDurationSeconds);
        return ResponseEntity.ok("Progress saved.");
    }

    /**
     * Returns incomplete watch history ordered by most recently watched.
     * GET /api/v1/me/history/continue-watching
     */
    @GetMapping("/continue-watching")
    public ResponseEntity<List<WatchHistoryDTO>> getContinueWatching(Authentication authentication) {
        List<WatchHistoryDTO> history = watchHistoryService.getActiveWatchHistory(authentication);
        return ResponseEntity.ok(history);
    }
}
