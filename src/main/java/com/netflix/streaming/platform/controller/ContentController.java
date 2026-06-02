package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.payload.ContentResponse;
import com.netflix.streaming.platform.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    @Autowired
    private ContentService contentService;

    @GetMapping("/public/all")
    public ResponseEntity<ContentResponse> getAllContent(
            @RequestParam(name = "pageNumber", defaultValue = "0", required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "10", required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = "releaseYear", required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = "desc", required = false) String sortOrder
    ) {
        ContentResponse response = contentService.getAllContent(pageNumber, pageSize, sortBy, sortOrder);
        return ResponseEntity.ok(response);
    }
}