package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.model.PlanTier;
import com.netflix.streaming.platform.payload.ContentDTO;
import com.netflix.streaming.platform.payload.EpisodeDTO;
import com.netflix.streaming.platform.repositories.ContentRepository;
import com.netflix.streaming.platform.repositories.UserRepository;
import com.netflix.streaming.platform.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {

    @Autowired
    private ContentService contentService;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private UserRepository userRepository;

    // ──────────────────────────────────────────────────────────────────────────
    // 📊 ADMIN STATS — Real numbers from the database, not hardcoded fakes
    // GET /api/admin/content/stats
    // Returns: { totalContent, activeUsers, premiumSubs }
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getAdminStats() {
        long totalContent  = contentRepository.count();
        long activeUsers   = userRepository.countByIsActiveTrue();
        long premiumSubs   = userRepository.countByPlanTierNot(PlanTier.NONE);

        return ResponseEntity.ok(Map.of(
                "totalContent", totalContent,
                "activeUsers",  activeUsers,
                "premiumSubs",  premiumSubs
        ));
    }

    @PostMapping(value = "/movie", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadMovie(
            @RequestPart("movieDetails") ContentDTO contentDTO,
            @RequestPart(value = "poster", required = false) MultipartFile poster,
            @RequestPart(value = "banner", required = false) MultipartFile banner,
            @RequestPart(value = "video", required = false) MultipartFile video) {

        try {
            ContentDTO savedMovie = contentService.addStandaloneMovie(contentDTO, poster, banner, video);
            return new ResponseEntity<>(savedMovie, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to upload movie: " + e.getMessage());
        }
    }

    // ==========================================
    // 📺 WEB SERIES UPLOAD PIPELINE
    // ==========================================

    // STEP 1: Create the Series (Metadata & Images only, NO video yet)
    @PostMapping(value = "/series", consumes = {"multipart/form-data"})
    public ResponseEntity<?> createSeries(
            @RequestPart("seriesDetails") ContentDTO contentDTO,
            @RequestPart(value = "poster", required = false) MultipartFile poster,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {

        try {
            ContentDTO savedSeries = contentService.createSeriesShell(contentDTO, poster, banner);
            return new ResponseEntity<>(savedSeries, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to create series: " + e.getMessage());
        }
    }

    // STEP 2: Add a Season to the Series
    @PostMapping("/series/{seriesId}/seasons")
    public ResponseEntity<?> addSeason(
            @PathVariable Long seriesId,
            @RequestParam("seasonNumber") Integer seasonNumber,
            @RequestParam("seasonTitle") String seasonTitle) {

        try {
            Object savedSeason = contentService.addSeasonToSeries(seriesId, seasonNumber, seasonTitle);
            return new ResponseEntity<>(savedSeason, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to add season: " + e.getMessage());
        }
    }

    // STEP 3: Upload an Episode to a specific Season (This handles the heavy FFmpeg video upload)
    @PostMapping(value = "/seasons/{seasonId}/episodes", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadEpisode(
            @PathVariable Long seasonId,
            @RequestPart("episodeDetails") EpisodeDTO episodeDTO,
            @RequestPart(value = "video", required = true) MultipartFile video) {

        try {
            Object savedEpisode = contentService.uploadEpisode(seasonId, episodeDTO, video);
            return new ResponseEntity<>(savedEpisode, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to upload episode: " + e.getMessage());
        }
    }
}