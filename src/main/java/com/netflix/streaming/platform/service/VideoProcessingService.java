package com.netflix.streaming.platform.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class VideoProcessingService {

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

        System.out.println(
                "HLS Conversion Complete! Master Playlist: "
                        + outputDirectory
                        + "/master.m3u8"
        );
    }

    private static Process getProcess(
            String inputFilePath,
            String outputDirectory
    ) throws IOException {

        /*
         * HLS output patterns
         *
         * ProcessBuilder directly launches ffmpeg.exe.
         * It does NOT pass these arguments through cmd.exe.
         */
        String segmentPattern =
                outputDirectory + "/stream_%v_segment_%03d.ts";

        String streamPlaylists =
                outputDirectory + "/stream_%v.m3u8";

        String[] command = {

                "ffmpeg",

                // INPUT
                "-i", inputFilePath,


                // MAP INPUT TO 3 VIDEO + AUDIO VARIANTS

                "-map", "0:v:0",
                "-map", "0:a:0",

                "-map", "0:v:0",
                "-map", "0:a:0",

                "-map", "0:v:0",
                "-map", "0:a:0",



                // AMD GPU VIDEO ENCODING


                // OLD:
                // "-c:v", "libx264",

                // NEW:
                // AMD AMF hardware H.264 encoder
                "-c:v", "h264_amf",

                // Audio remains CPU-based AAC encoding
                "-c:a", "aac",

                "-ar", "48000",


                // GOP / KEYFRAME SETTINGS

                "-keyint_min", "48",
                "-g", "48",
                "-sc_threshold", "0",

                // 1080p

                "-filter:v:0",
                "scale=-2:1080",

                "-b:v:0", "5000k",
                "-maxrate:v:0", "5300k",
                "-bufsize:v:0", "7500k",

                "-b:a:0", "192k",

                // 720p

                "-filter:v:1",
                "scale=-2:720",

                "-b:v:1", "2800k",
                "-maxrate:v:1", "3000k",
                "-bufsize:v:1", "4200k",

                "-b:a:1", "128k",

                // 480p

                "-filter:v:2",
                "scale=-2:480",

                "-b:v:2", "1400k",
                "-maxrate:v:2", "1500k",
                "-bufsize:v:2", "2100k",

                "-b:a:2", "96k",

                // HLS

                "-f", "hls",

                "-hls_time", "10",

                // VOD playlist
                "-hls_playlist_type", "vod",

                // Every variant can be independently decoded
                "-hls_flags", "independent_segments",


                // ============================================================
                // HLS SEGMENT FILES
                // ============================================================

                "-hls_segment_filename",
                segmentPattern,


                // ============================================================
                // MASTER PLAYLIST
                // ============================================================

                "-master_pl_name",
                "master.m3u8",

                "-var_stream_map",
                "v:0,a:0 v:1,a:1 v:2,a:2",


                // ============================================================
                // VARIANT PLAYLISTS
                // ============================================================

                streamPlaylists
        };

        /*
         * ProcessBuilder directly executes FFmpeg.
         * No cmd.exe → no Windows % expansion.
         */
        ProcessBuilder processBuilder =
                new ProcessBuilder(command);

        // Combine stdout + stderr
        processBuilder.redirectErrorStream(true);

        return processBuilder.start();
    }
}