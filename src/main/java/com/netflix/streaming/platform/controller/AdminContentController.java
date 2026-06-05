package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.payload.ContentDTO;
import com.netflix.streaming.platform.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {

    @Autowired
    private ContentService contentService;

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
}