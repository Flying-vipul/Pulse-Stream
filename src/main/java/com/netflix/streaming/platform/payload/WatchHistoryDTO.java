package com.netflix.streaming.platform.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
public class WatchHistoryDTO {

    private Long contentId;
    private int lastWatchedSeconds;


    private String title;
    private String thumbnailUrl;
    private Long episodeId;
    private String episodeTitle;
    private Integer totalDurationSeconds;

    public WatchHistoryDTO() {}

}