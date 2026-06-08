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

        // 1. Ensure the output folder exists
        Path outputDir = Paths.get(outputDirectory);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // 2. Run the multi-quality FFmpeg transcode
        Process process = getProcess(inputFilePath, outputDirectory);

        // 3. Drain stdout/stderr so the process buffer never deadlocks
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFmpeg Worker] " + line);
            }
        }

        // 4. Check exit code
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg processing failed with exit code: " + exitCode);
        }

        System.out.println("🎬 HLS Conversion Complete! Master Playlist: " + outputDirectory + "/master.m3u8");
    }

    private static Process getProcess(String inputFilePath, String outputDirectory) throws IOException {

        // ─────────────────────────────────────────────────────────────────────────
        // 🛡️ WINDOWS SAFE: Why this layout is critical
        //
        // On Windows, when you build a command string like:
        //   outputDirectory + "/stream_%v_segment_%03d.ts"
        // and pass it through cmd.exe, Windows expands %v% and %03d% as
        // environment variables → they get erased → FFmpeg sees broken paths
        // → master.m3u8 ends up referencing "%03d.ts" literally.
        //
        // FIX: ProcessBuilder does NOT invoke cmd.exe. It passes each array
        // element as a raw argument directly to the child process (ffmpeg.exe).
        // So %03d and %v survive intact AS LONG AS they are in their OWN
        // String variable — never concatenated inside a single string with
        // the path using + operator at the call site.
        //
        // We pre-build the three path strings here, then each becomes its
        // own array slot → ffmpeg receives them exactly as written. ✅
        // ─────────────────────────────────────────────────────────────────────────

        // Each of these is a standalone String — no shell expansion risk.
        String segmentPattern  = outputDirectory + "/stream_%v_segment_%03d.ts";
        String masterPlaylist  = outputDirectory + "/master.m3u8";
        String streamPlaylists = outputDirectory + "/stream_%v.m3u8";

        String[] command = {
                "ffmpeg",
                "-i", inputFilePath,

                // Map input → 3 quality streams (1080p / 720p / 480p)
                "-map", "0:v:0", "-map", "0:a:0",
                "-map", "0:v:0", "-map", "0:a:0",
                "-map", "0:v:0", "-map", "0:a:0",

                // Video + Audio codec settings
                "-c:v", "libx264",
                "-c:a", "aac",
                "-ar",  "48000",
                "-keyint_min", "48",
                "-g",   "48",
                "-sc_threshold", "0",

                // Stream 0 — 1080p @ 5 Mbps
                "-filter:v:0", "scale=-2:1080",
                "-b:v:0", "5000k", "-maxrate:v:0", "5300k", "-bufsize:v:0", "7500k",
                "-b:a:0", "192k",

                // Stream 1 — 720p @ 2.8 Mbps
                "-filter:v:1", "scale=-2:720",
                "-b:v:1", "2800k", "-maxrate:v:1", "3000k", "-bufsize:v:1", "4200k",
                "-b:a:1", "128k",

                // Stream 2 — 480p @ 1.4 Mbps
                "-filter:v:2", "scale=-2:480",
                "-b:v:2", "1400k", "-maxrate:v:2", "1500k", "-bufsize:v:2", "2100k",
                "-b:a:2", "96k",

                // HLS container settings
                "-f",                 "hls",
                "-hls_time",          "10",
                "-hls_playlist_type", "vod",
                "-hls_flags",         "independent_segments",

                // 🛡️ KEY: -hls_segment_filename as its own argv slot.
                // ProcessBuilder sends this string raw to ffmpeg — %03d/%v are safe.
                "-hls_segment_filename", segmentPattern,

                // Master playlist name + adaptive stream mapping
                "-master_pl_name", "master.m3u8",
                "-var_stream_map", "v:0,a:0 v:1,a:1 v:2,a:2",

                // Sub-playlist output pattern (also its own argv slot — safe)
                streamPlaylists
        };

        // ProcessBuilder bypasses cmd.exe entirely → no % variable expansion
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
    }
}