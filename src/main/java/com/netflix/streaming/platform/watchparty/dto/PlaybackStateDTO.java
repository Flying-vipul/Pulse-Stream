package com.netflix.streaming.platform.watchparty.dto;

import com.netflix.streaming.platform.watchparty.domain.PlaybackState;
import com.netflix.streaming.platform.watchparty.domain.PlaybackStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackStateDTO {
    private PlaybackStatus status;
    private long positionMs;
    private long anchorServerTimeMs;
    private long sequence;
    private double playbackRate;
    private long calculatedPositionMs;

    public static PlaybackStateDTO from(PlaybackState state) {
        if (state == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        return PlaybackStateDTO.builder()
                .status(state.getStatus())
                .positionMs(state.getPositionMs())
                .anchorServerTimeMs(state.getAnchorServerTimeMs())
                .sequence(state.getSequence())
                .playbackRate(state.getPlaybackRate())
                .calculatedPositionMs(state.calculateExpectedPositionMs(now))
                .build();
    }
}
