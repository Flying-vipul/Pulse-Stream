package com.netflix.streaming.platform.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WatchHistoryDTO {

    private Long contentId;
    private Long episodeId;
    private String title;
    private String episodeTitle;
    private String thumbnailUrl;
    private int currentPositionSeconds;
    private int durationSeconds;
    private double progressPercentage;
    private boolean completed;
    private LocalDateTime lastWatchedAt;

    // Optional fields for episode details
    private Integer seasonNumber;
    private Integer episodeNumber;

}