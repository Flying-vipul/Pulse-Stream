package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.Episode;
import com.netflix.streaming.platform.repositories.ContentRepository;
import com.netflix.streaming.platform.repositories.EpisodeRepository;
import com.netflix.streaming.platform.service.AzureBlobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/migrate")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class MigrationController {

    private static final Logger log = LoggerFactory.getLogger(MigrationController.class);
    
    // We dynamically find the java.io.tmpdir base path like ContentServiceImpl does
    private static final String OLD_HLS_BASE = System.getProperty("java.io.tmpdir") + File.separator + "pulsestream_hls";

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private EpisodeRepository episodeRepository;

    @Autowired
    private AzureBlobService azureBlobService;

    @GetMapping("/dry-run")
    public ResponseEntity<Map<String, Object>> getMigrationStatus() {
        List<String> readyForMigration = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<Content> allContent = contentRepository.findAll();
        for (Content c : allContent) {
            String v = c.getVideoUrl();
            if (v != null && v.startsWith("/videos/")) {
                readyForMigration.add("MOVIE: " + c.getTitle() + " -> " + v);
            } else if (v != null && (v.startsWith("https://") && v.contains("%2F"))) {
                warnings.add("MOVIE: " + c.getTitle() + " -> BROKEN AZURE URL (needs re-migration)");
            } else if (v != null && v.startsWith("https://")) {
                warnings.add("MOVIE: " + c.getTitle() + " -> ALREADY IN AZURE");
            }
        }

        List<Episode> allEp = episodeRepository.findAll();
        for (Episode ep : allEp) {
            String v = ep.getVideoUrl();
            if (v != null && v.startsWith("/videos/")) {
                readyForMigration.add("EPISODE: " + ep.getTitle() + " -> " + v);
            } else if (v != null && (v.startsWith("https://") && v.contains("%2F"))) {
                warnings.add("EPISODE: " + ep.getTitle() + " -> BROKEN AZURE URL (needs re-migration)");
            } else if (v != null && v.startsWith("https://")) {
                warnings.add("EPISODE: " + ep.getTitle() + " -> ALREADY IN AZURE");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readyCount", readyForMigration.size());
        result.put("readyFiles", readyForMigration);
        result.put("warningDetails", warnings);
        result.put("message", "This is a DRY RUN — call POST /api/admin/migrate/hls-to-azure to execute.");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/hls-to-azure")
    public ResponseEntity<Map<String, Object>> migrateAllToAzure(
            @RequestParam(defaultValue = "false") boolean deleteLocalAfter) {

        List<String> migrated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // ── 1. Migrate all MOVIES ──────────────────────────────────────────
        List<Content> allContent = contentRepository.findAll();
        for (Content content : allContent) {
            String videoUrl = content.getVideoUrl();

            if (videoUrl == null || videoUrl.isBlank() || (videoUrl.startsWith("https://") && !videoUrl.contains("%2F"))) {
                skipped.add("Content id=" + content.getId() + " → already in Azure or no video");
                continue;
            }

            String folderName;
            if (videoUrl.startsWith("/videos/")) {
                folderName = videoUrl.replace("/videos/", "").replace("/master.m3u8", "");
            } else {
                try {
                    folderName = java.net.URLDecoder.decode(videoUrl, java.nio.charset.StandardCharsets.UTF_8.name())
                            .replaceAll(".*videos/", "").replace("/master.m3u8", "");
                } catch (Exception ex) {
                    folderName = videoUrl.replaceAll(".*videos/", "").replace("/master.m3u8", "");
                }
            }

            String localPath = OLD_HLS_BASE + "/" + folderName;
            String azureFolder = folderName.startsWith("series_") ? "series/" + folderName : "movies/" + folderName;

            try {
                log.info("☁️  Migrating: {} → {}", localPath, azureFolder);
                String azureUrl = azureBlobService.uploadHlsFolderToAzure(localPath, azureFolder);
                content.setVideoUrl(azureUrl);
                contentRepository.save(content);

                if (deleteLocalAfter) azureBlobService.cleanupLocalDirectory(localPath);
                migrated.add("✅ Content id=" + content.getId() + " → " + azureUrl);
            } catch (Exception e) {
                errors.add("❌ Content id=" + content.getId() + " FAILED: " + e.getMessage());
            }
        }

        // ── 2. Migrate all EPISODES ────────────────────────────────────────
        List<Episode> allEpisodes = episodeRepository.findAll();
        for (Episode ep : allEpisodes) {
            String videoUrl = ep.getVideoUrl();

            if (videoUrl == null || videoUrl.isBlank() || (videoUrl.startsWith("https://") && !videoUrl.contains("%2F"))
                    || videoUrl.equals("processing_video...") || videoUrl.equals("uploading_to_azure...")) {
                skipped.add("Episode id=" + ep.getId() + " → already in Azure or no video");
                continue;
            }

            String folderName;
            if (videoUrl.startsWith("/videos/")) {
                folderName = videoUrl.replace("/videos/", "").replace("/master.m3u8", "");
            } else {
                try {
                    folderName = java.net.URLDecoder.decode(videoUrl, java.nio.charset.StandardCharsets.UTF_8.name())
                            .replaceAll(".*videos/", "").replace("/master.m3u8", "");
                } catch (Exception ex) {
                    folderName = videoUrl.replaceAll(".*videos/", "").replace("/master.m3u8", "");
                }
            }

            String localPath = OLD_HLS_BASE + "/" + folderName;
            String azureFolder = "series/" + folderName;

            try {
                log.info("☁️  Migrating episode: {} → {}", localPath, azureFolder);
                String azureUrl = azureBlobService.uploadHlsFolderToAzure(localPath, azureFolder);
                ep.setVideoUrl(azureUrl);
                episodeRepository.save(ep);

                if (deleteLocalAfter) azureBlobService.cleanupLocalDirectory(localPath);
                migrated.add("✅ Episode id=" + ep.getId() + " → " + azureUrl);
            } catch (Exception e) {
                errors.add("❌ Episode id=" + ep.getId() + " FAILED: " + e.getMessage());
            }
        }

        // ── 3. Build response summary ──────────────────────────────────────
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", errors.isEmpty() ? "✅ MIGRATION COMPLETE" : "⚠️ COMPLETED WITH ERRORS");
        result.put("migrated", migrated.size());
        result.put("skipped", skipped.size());
        result.put("errors", errors.size());
        result.put("migratedDetails", migrated);
        result.put("skippedDetails", skipped);
        result.put("errorDetails", errors);
        result.put("localFilesDeleted", deleteLocalAfter);

        return ResponseEntity.ok(result);
    }

    /**
     * ⚡ INSTANT FIX: Decode %2F-encoded Azure URLs in the database.
     * No re-upload needed — just fixes the stored URLs so HLS.js can work.
     * POST /api/admin/migrate/fix-encoded-urls
     */
    @PostMapping("/fix-encoded-urls")
    public ResponseEntity<Map<String, Object>> fixEncodedUrls() {
        List<String> fixed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        // Fix Content (movies)
        for (Content content : contentRepository.findAll()) {
            String url = content.getVideoUrl();
            if (url != null && url.startsWith("https://") && url.contains("%2F")) {
                try {
                    String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8.name());
                    content.setVideoUrl(decoded);
                    contentRepository.save(content);
                    fixed.add("✅ Content id=" + content.getId() + ": " + decoded);
                    log.info("Fixed URL for content id={}", content.getId());
                } catch (Exception e) {
                    skipped.add("❌ Content id=" + content.getId() + ": " + e.getMessage());
                }
            } else {
                skipped.add("Content id=" + content.getId() + " → no fix needed");
            }
        }

        // Fix Episodes
        for (Episode ep : episodeRepository.findAll()) {
            String url = ep.getVideoUrl();
            if (url != null && url.startsWith("https://") && url.contains("%2F")) {
                try {
                    String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8.name());
                    ep.setVideoUrl(decoded);
                    episodeRepository.save(ep);
                    fixed.add("✅ Episode id=" + ep.getId() + ": " + decoded);
                    log.info("Fixed URL for episode id={}", ep.getId());
                } catch (Exception e) {
                    skipped.add("❌ Episode id=" + ep.getId() + ": " + e.getMessage());
                }
            } else {
                skipped.add("Episode id=" + ep.getId() + " → no fix needed");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "✅ DONE");
        result.put("fixed", fixed.size());
        result.put("fixedDetails", fixed);
        result.put("skipped", skipped.size());
        return ResponseEntity.ok(result);
    }
}
