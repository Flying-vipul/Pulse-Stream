package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.model.MediaType;
import com.netflix.streaming.platform.payload.ContentDTO;
import com.netflix.streaming.platform.payload.ContentResponse;
import com.netflix.streaming.platform.payload.EpisodeDTO;
import com.netflix.streaming.platform.payload.SeasonDTO;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ContentService {

    // --- Public content browsing ---
    ContentResponse getAllContent(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    ContentResponse getContentByType(MediaType type, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    // --- Admin upload pipeline ---
    ContentDTO addStandaloneMovie(ContentDTO contentDTO, MultipartFile poster, MultipartFile banner, MultipartFile videoFile) throws Exception;
    ContentDTO createSeriesShell(ContentDTO contentDTO, MultipartFile poster, MultipartFile banner) throws Exception;
    SeasonDTO addSeasonToSeries(Long seriesId, Integer seasonNumber, String seasonTitle) throws Exception;
    EpisodeDTO uploadEpisode(Long seasonId, EpisodeDTO episodeDTO, MultipartFile videoFile) throws Exception;

    // --- Watchlist (user-level, derived from JWT — never from URL userId) ---
    List<ContentDTO> getWatchlist(Authentication auth);
    Map<String, Object> addToWatchlist(Authentication auth, Long contentId);
    Map<String, Object> removeFromWatchlist(Authentication auth, Long contentId);
    Map<String, Object> checkWatchlist(Authentication auth, Long contentId);
}
