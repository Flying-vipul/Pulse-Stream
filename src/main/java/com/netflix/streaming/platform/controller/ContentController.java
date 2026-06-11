package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.model.MediaType;
import com.netflix.streaming.platform.payload.ContentDTO;
import com.netflix.streaming.platform.payload.ContentResponse;
import com.netflix.streaming.platform.repositories.ContentRepository;
import com.netflix.streaming.platform.repositories.UserRepository;
import com.netflix.streaming.platform.service.ContentService;
import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    @Autowired private ContentService contentService;
    @Autowired private ContentRepository contentRepository;
    @Autowired private UserRepository userRepository;

    // ── ALL CONTENT (paginated) ───────────────────────────────────────────────
    @GetMapping("/public/all")
    public ResponseEntity<ContentResponse> getAllContent(
            @RequestParam(defaultValue = "0") Integer pageNumber,
            @RequestParam(defaultValue = "18") Integer pageSize,
            @RequestParam(defaultValue = "releaseYear") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return ResponseEntity.ok(contentService.getAllContent(pageNumber, pageSize, sortBy, sortOrder));
    }

    // ── BY TYPE: MOVIE or SERIES (paginated) ─────────────────────────────────
    @GetMapping("/public/by-type")
    public ResponseEntity<ContentResponse> getByType(
            @RequestParam String type,
            @RequestParam(defaultValue = "0") Integer pageNumber,
            @RequestParam(defaultValue = "18") Integer pageSize,
            @RequestParam(defaultValue = "releaseYear") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        MediaType mediaType = MediaType.valueOf(type.toUpperCase());
        return ResponseEntity.ok(contentService.getContentByType(mediaType, pageNumber, pageSize, sortBy, sortOrder));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WATCHLIST — persisted to the `user_watchlist` DB table via @ElementCollection
    // on the User entity. Data survives server restarts.
    // ══════════════════════════════════════════════════════════════════════════

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private ContentDTO toDTO(Content c) {
        ContentDTO dto = new ContentDTO();
        dto.setId(c.getId());
        dto.setTitle(c.getTitle());
        dto.setDescription(c.getDescription());
        dto.setReleaseYear(c.getReleaseYear());
        dto.setThumbnailUrl(c.getThumbnailUrl());
        dto.setBannerUrl(c.getBannerUrl());
        dto.setContentType(c.getContentType());
        dto.setDurationMinutes(c.getDurationMinutes());
        return dto;
    }

    // GET /api/content/watchlist/{userId}
    @GetMapping("/watchlist/{userId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ContentDTO>> getWatchlist(@PathVariable Long userId) {
        User user = getUser(userId);
        Set<Long> ids = user.getWatchlistContentIds();
        if (ids == null || ids.isEmpty()) return ResponseEntity.ok(Collections.emptyList());

        List<Content> items = contentRepository.findByIdIn(ids);
        List<ContentDTO> dtos = items.stream().map(this::toDTO).toList();
        return ResponseEntity.ok(dtos);
    }

    // POST /api/content/watchlist/{userId}/add/{contentId}
    @PostMapping("/watchlist/{userId}/add/{contentId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> addToWatchlist(
            @PathVariable Long userId,
            @PathVariable Long contentId) {

        User user = getUser(userId);
        user.getWatchlistContentIds().add(contentId);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("inWatchlist", true, "contentId", contentId));
    }

    // DELETE /api/content/watchlist/{userId}/remove/{contentId}
    @DeleteMapping("/watchlist/{userId}/remove/{contentId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeFromWatchlist(
            @PathVariable Long userId,
            @PathVariable Long contentId) {

        User user = getUser(userId);
        user.getWatchlistContentIds().remove(contentId);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("inWatchlist", false, "contentId", contentId));
    }

    // GET /api/content/watchlist/{userId}/check/{contentId}
    @GetMapping("/watchlist/{userId}/check/{contentId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> checkWatchlist(
            @PathVariable Long userId,
            @PathVariable Long contentId) {

        User user = getUser(userId);
        boolean inList = user.getWatchlistContentIds().contains(contentId);
        return ResponseEntity.ok(Map.of("inWatchlist", inList, "contentId", contentId));
    }
}