package com.netflix.streaming.platform.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatRequest {
    private Long contentId;
    private Long episodeId;
    private int positionSeconds;
}
