package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.payload.WatchHistoryDTO;
import com.netflix.streaming.platform.repositories.ProfileRepository;
import com.netflix.streaming.platform.repositories.UserRepository;
import com.netflix.streaming.platform.service.WatchHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class WatchHistoryController {

    @Autowired
    private WatchHistoryService watchHistoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // 🛡️ USER-BASED ENDPOINTS (Frontend uses user.id, not profileId)
    // These are the REAL endpoints the frontend calls.
    // Internally they auto-find the user's first profile.
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/api/users/{userId}/history/heartbeat")
    public ResponseEntity<?> saveProgressByUser(
            @PathVariable Long userId,
            @RequestParam Long contentId,
            @RequestParam(required = false) Long episodeId,
            @RequestParam int watchedSeconds,
            @RequestParam int totalDurationSeconds) {

        Long profileId = resolveProfileId(userId);
        watchHistoryService.updateProgress(profileId, contentId, episodeId, watchedSeconds, totalDurationSeconds);
        return ResponseEntity.ok("Progress saved.");
    }

    @GetMapping("/api/users/{userId}/history/continue-watching")
    public ResponseEntity<List<WatchHistoryDTO>> getContinueWatchingByUser(@PathVariable Long userId) {
        Long profileId = resolveProfileId(userId);
        List<WatchHistoryDTO> historyList = watchHistoryService.getActiveWatchHistory(profileId);
        return ResponseEntity.ok(historyList);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEGACY: Original profile-based endpoints (kept for compatibility)
    // ─────────────────────────────────────────────────────────────────────────

    @RequestMapping("/api/profiles/{profileId}/history/heartbeat")
    public ResponseEntity<?> saveProgress(
            @PathVariable Long profileId,
            @RequestParam Long contentId,
            @RequestParam(required = false) Long episodeId,
            @RequestParam int watchedSeconds,
            @RequestParam int totalDurationSeconds) {

        watchHistoryService.updateProgress(profileId, contentId, episodeId, watchedSeconds, totalDurationSeconds);
        return ResponseEntity.ok("Progress saved.");
    }

    @GetMapping("/api/profiles/{profileId}/history/continue-watching")
    public ResponseEntity<List<WatchHistoryDTO>> getContinueWatching(@PathVariable Long profileId) {
        List<WatchHistoryDTO> historyList = watchHistoryService.getActiveWatchHistory(profileId);
        return ResponseEntity.ok(historyList);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER: Resolve the first profile ID for a given user
    // ─────────────────────────────────────────────────────────────────────────
    private Long resolveProfileId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        List<Profile> profiles = profileRepository.findByUser(user);
        if (profiles.isEmpty()) {
            throw new ResourceNotFoundException("Profile", "userId", userId);
        }
        return profiles.get(0).getId(); // Use the first profile
    }
}