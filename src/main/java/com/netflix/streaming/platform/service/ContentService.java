package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.model.MediaType;
import com.netflix.streaming.platform.payload.ContentDTO;
import com.netflix.streaming.platform.payload.ContentResponse;
import com.netflix.streaming.platform.payload.SeasonDTO; // You will need to create this
import com.netflix.streaming.platform.payload.EpisodeDTO; // You will need to create this
import org.springframework.web.multipart.MultipartFile;

public interface ContentService {
    ContentResponse getAllContent(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    ContentResponse getContentByType(MediaType type, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    // Movie Pipeline
    ContentDTO addStandaloneMovie(ContentDTO contentDTO, MultipartFile poster, MultipartFile banner, MultipartFile videoFile) throws Exception;

    // Web Series Pipeline
    ContentDTO createSeriesShell(ContentDTO contentDTO, MultipartFile poster, MultipartFile banner) throws Exception;
    SeasonDTO addSeasonToSeries(Long seriesId, Integer seasonNumber, String seasonTitle) throws Exception;
    EpisodeDTO uploadEpisode(Long seasonId, EpisodeDTO episodeDTO, MultipartFile videoFile) throws Exception;
}