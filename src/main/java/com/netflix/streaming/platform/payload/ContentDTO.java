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

    // We will dynamically construct these in the Service!
    private String thumbnailUrl;
    private String bannerUrl;

    // Only populated if it's a standalone Movie
    private String videoUrl;
    private Integer durationMinutes;

    // Downward flowing relationships
    private Set<GenreDTO> genres;
    private List<SeasonDTO> seasons;
}