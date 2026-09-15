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
import org.springframework.security.core.Authentication;

import java.util.*;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    @Autowired private ContentService contentService;
    @Autowired private ContentRepository contentRepository;
    @Autowired private UserRepository userRepository;

    @GetMapping("/public/all")
    public ResponseEntity<ContentResponse> getAllContent(
            @RequestParam(defaultValue = "0") Integer pageNumber,
            @RequestParam(defaultValue = "18") Integer pageSize,
            @RequestParam(defaultValue = "releaseYear") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return ResponseEntity.ok(contentService.getAllContent(pageNumber, pageSize, sortBy, sortOrder));
    }

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

    @GetMapping("/v1/me/watchlist")
    public ResponseEntity<List<ContentDTO>> getWatchlist(Authentication authentication) {
        return ResponseEntity.ok(contentService.getWatchlist(authentication));
    }

    @PostMapping("/v1/me/watchlist/{contentId}")
    public ResponseEntity<Map<String, Object>> addToWatchlist(
            Authentication authentication,
            @PathVariable Long contentId) {
        return ResponseEntity.ok(contentService.addToWatchlist(authentication, contentId));
    }

    @DeleteMapping("/v1/me/watchlist/{contentId}")
    public ResponseEntity<Map<String, Object>> removeFromWatchlist(
            Authentication authentication,
            @PathVariable Long contentId) {
        return ResponseEntity.ok(contentService.removeFromWatchlist(authentication, contentId));
    }

    @GetMapping("/v1/me/watchlist/check/{contentId}")
    public ResponseEntity<Map<String, Object>> checkWatchlist(
            Authentication authentication,
            @PathVariable Long contentId) {
        return ResponseEntity.ok(contentService.checkWatchlist(authentication, contentId));
    }
}