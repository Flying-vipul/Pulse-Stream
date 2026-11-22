package com.netflix.streaming.platform.watchparty.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration properties for LiveKit WebRTC SFU.
 * Loaded from 'livekit.*' in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "livekit")
@Data
public class LiveKitProperties {

    /**
     * LiveKit WebSocket/WebRTC SFU URL (e.g. wss://project.livekit.cloud).
     */
    private String url = "https://pulsestream.livekit.cloud";

    /**
     * LiveKit API Key from LiveKit Cloud or self-hosted deployment.
     */
    private String apiKey;

    /**
     * LiveKit API Secret (must never be exposed to frontend or logged).
     */
    private String apiSecret;

    /**
     * Returns true if API key and secret are configured.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }
}

