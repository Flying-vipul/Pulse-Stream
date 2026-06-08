package com.netflix.streaming.platform.payload;

import lombok.Data;

@Data
public class EpisodeDTO {
    private Long id;
    private Integer episodeNumber;
    private String title;
    private String videoUrl;
    private Integer durationMinutes;
}