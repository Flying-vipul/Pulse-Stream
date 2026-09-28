package com.netflix.streaming.platform.payload;

import com.netflix.streaming.platform.model.MediaType;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class ContentDTO {
    private Long id;
    private String title;
    private String description;
    private MediaType contentType;
    private Integer releaseYear;
    private String thumbnailUrl;
    private String bannerUrl;
    private String videoUrl;
    private Integer durationMinutes;
    private Set<GenreDTO> genres;
    private List<SeasonDTO> seasons;
}