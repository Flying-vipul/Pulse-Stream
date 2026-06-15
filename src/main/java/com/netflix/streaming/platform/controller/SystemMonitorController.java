package com.netflix.streaming.platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/admin/system")
public class SystemMonitorController {

    // The exact path we are monitoring
    private final String STORAGE_PATH = "C:/Users/Vipul/Videos/PulseStream/";

    @GetMapping("/storage")
    // @PreAuthorize("hasRole('ADMIN')") // Uncomment if you are using strict role-based access
    public ResponseEntity<Map<String, Object>> getStorageStats() {
        Map<String, Object> stats = new HashMap<>();
        File storageFolder = new File(STORAGE_PATH);

        // 1. Get entire C: Drive limits
        long totalSpace = storageFolder.getTotalSpace();
        long freeSpace = storageFolder.getFreeSpace();

        // 2. Calculate exactly how much space PulseStream is taking
        long pulseStreamUsage = calculateDirectorySize(Paths.get(STORAGE_PATH));

        // 3. Format to Gigabytes (GB) for the frontend
        stats.put("driveTotalGB", formatToGB(totalSpace));
        stats.put("driveFreeGB", formatToGB(freeSpace));
        stats.put("pulseStreamUsageGB", formatToGB(pulseStreamUsage));
        stats.put("usagePercentage", String.format("%.1f", ((double) pulseStreamUsage / totalSpace) * 100));

        return ResponseEntity.ok(stats);
    }

    // Helper method to recursively count file sizes
    private long calculateDirectorySize(Path path) {
        if (!Files.exists(path)) return 0;

        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            System.err.println("Failed to calculate storage size: " + e.getMessage());
            return 0;
        }
    }

    // Helper method to convert raw bytes to Gigabytes with 2 decimal places
    private double formatToGB(long bytes) {
        return Math.round((bytes / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;
    }
}