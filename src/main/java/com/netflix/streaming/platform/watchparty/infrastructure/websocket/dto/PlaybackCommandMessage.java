package com.netflix.streaming.platform.watchparty.infrastructure.websocket.dto;

import com.netflix.streaming.platform.watchparty.domain.PlaybackCommandType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound playback command sent by host over STOMP to /app/watch-parties/{roomId}/playback.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackCommandMessage {

    /** Action: PLAY, PAUSE, or SEEK */
    private PlaybackCommandType action;

    /** Desired or current position in milliseconds */
    private long positionMs;

    /** Playback rate (defaults to 1.0) */
    @Builder.Default
    private double playbackRate = 1.0;

    /** Optional sequence number for idempotency and ordering checks */
    private Long sequence;
}

