package com.netflix.streaming.platform.payload;

import lombok.Data;
import java.util.List;

@Data
public class SeasonDTO {
    private Long id;
    private Integer seasonNumber;
    private String title;
    private Integer releaseYear;
    private List<EpisodeDTO> episodes; // Flows downwards only!
}