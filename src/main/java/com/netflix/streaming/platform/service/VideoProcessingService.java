package com.netflix.streaming.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class VideoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessingService.class);

    @Value("${app.ffmpeg.encoder:auto}")
    private String configuredEncoder;

    @Value("${app.ffmpeg.preset:veryfast}")
    private String configuredPreset;

    private volatile String resolvedEncoder = null;

    public void generateHlsStream(String inputFilePath, String outputDirectory) throws Exception {

        // 1. Ensure output directory exists
        Path outputDir = Paths.get(outputDirectory);

        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // 2. Start FFmpeg
        Process process = getProcess(inputFilePath, outputDirectory);

        // 3. Read FFmpeg output
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println("[FFmpeg Worker] " + line);
            }
        }

        // 4. Wait for FFmpeg
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException(
                    "FFmpeg processing failed with exit code: " + exitCode
            );
        }

        log.info("HLS Conversion Complete! Master Playlist: {}/master.m3u8", outputDirectory);
    }

    private Process getProcess(
            String inputFilePath,
            String outputDirectory
    ) throws IOException {

        String encoder = getOrResolveEncoder();
        log.info("Starting FFmpeg HLS transcode using video encoder: {}", encoder);

        String segmentPattern =
                outputDirectory + "/stream_%v_segment_%03d.ts";

        String streamPlaylists =
                outputDirectory + "/stream_%v.m3u8";

        List<String> command = new ArrayList<>();
        command.add("ffmpeg");

        // INPUT
        command.add("-i");
        command.add(inputFilePath);

        // MAP INPUT TO 3 VIDEO + AUDIO VARIANTS
        command.add("-map"); command.add("0:v:0");
        command.add("-map"); command.add("0:a:0");

        command.add("-map"); command.add("0:v:0");
        command.add("-map"); command.add("0:a:0");

        command.add("-map"); command.add("0:v:0");
        command.add("-map"); command.add("0:a:0");

        // VIDEO ENCODER
        command.add("-c:v");
        command.add(encoder);

        // If using CPU encoder (libx264), use fast preset and all available CPU threads
        if ("libx264".equalsIgnoreCase(encoder)) {
            command.add("-preset");
            command.add(configuredPreset != null && !configuredPreset.isBlank() ? configuredPreset : "veryfast");
            command.add("-threads");
            command.add("0"); // auto-detect all available CPU cores (e.g. 4 OCPUs on OCI)
        }

        // Audio remains CPU-based AAC encoding
        command.add("-c:a"); command.add("aac");
        command.add("-ar"); command.add("48000");

        // GOP / KEYFRAME SETTINGS
        command.add("-keyint_min"); command.add("48");
        command.add("-g"); command.add("48");
        command.add("-sc_threshold"); command.add("0");

        // 1080p
        command.add("-filter:v:0"); command.add("scale=-2:1080");
        command.add("-b:v:0"); command.add("5000k");
        command.add("-maxrate:v:0"); command.add("5300k");
        command.add("-bufsize:v:0"); command.add("7500k");
        command.add("-b:a:0"); command.add("192k");

        // 720p
        command.add("-filter:v:1"); command.add("scale=-2:720");
        command.add("-b:v:1"); command.add("2800k");
        command.add("-maxrate:v:1"); command.add("3000k");
        command.add("-bufsize:v:1"); command.add("4200k");
        command.add("-b:a:1"); command.add("128k");

        // 480p
        command.add("-filter:v:2"); command.add("scale=-2:480");
        command.add("-b:v:2"); command.add("1400k");
        command.add("-maxrate:v:2"); command.add("1500k");
        command.add("-bufsize:v:2"); command.add("2100k");
        command.add("-b:a:2"); command.add("96k");

        // HLS
        command.add("-f"); command.add("hls");
        command.add("-hls_time"); command.add("10");
        command.add("-hls_playlist_type"); command.add("vod");
        command.add("-hls_flags"); command.add("independent_segments");

        // HLS SEGMENT FILES
        command.add("-hls_segment_filename");
        command.add(segmentPattern);

        // MASTER PLAYLIST
        command.add("-master_pl_name");
        command.add("master.m3u8");

        command.add("-var_stream_map");
        command.add("v:0,a:0 v:1,a:1 v:2,a:2");

        // VARIANT PLAYLISTS
        command.add(streamPlaylists);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        return processBuilder.start();
    }

    private String getOrResolveEncoder() {
        if (resolvedEncoder != null) {
            return resolvedEncoder;
        }

        if (configuredEncoder != null && !configuredEncoder.isBlank() && !"auto".equalsIgnoreCase(configuredEncoder)) {
            resolvedEncoder = configuredEncoder.trim();
            log.info("Using explicitly configured video encoder: {}", resolvedEncoder);
            return resolvedEncoder;
        }

        // Automatic detection: test if h264_amf is available on this system
        if (isEncoderSupported("h264_amf")) {
            log.info("AMD AMF GPU hardware encoder detected (h264_amf). Using GPU acceleration.");
            resolvedEncoder = "h264_amf";
        } else {
            log.info("No AMD AMF GPU detected or running on cloud Linux. Using universal CPU encoder: libx264 (preset: {})", configuredPreset);
            resolvedEncoder = "libx264";
        }

        return resolvedEncoder;
    }

    private boolean isEncoderSupported(String encoderName) {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-hide_banner", "-encoders")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(encoderName)) {
                        process.waitFor();
                        return true;
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.warn("Could not probe FFmpeg encoders: {}. Defaulting to libx264.", e.getMessage());
        }
        return false;
    }
}
