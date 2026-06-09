package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.Episode;
import com.netflix.streaming.platform.model.Season;
import com.netflix.streaming.platform.payload.ContentDTO;
import com.netflix.streaming.platform.payload.ContentResponse;
import com.netflix.streaming.platform.payload.EpisodeDTO;
import com.netflix.streaming.platform.payload.SeasonDTO;
import com.netflix.streaming.platform.repositories.ContentRepository;
import com.netflix.streaming.platform.repositories.EpisodeRepository;
import com.netflix.streaming.platform.repositories.SeasonRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional
public class ContentServiceImpl implements ContentService {

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private EpisodeRepository episodeRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private VideoProcessingService videoProcessingService;

    @Value("${image.base.url:http://localhost:8080/images/}")
    private String imageBaseUrl;

    @Override
    public ContentResponse getAllContent(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Content> pageContent = contentRepository.findAll(pageDetails);

        return buildContentResponse(pageContent);
    }

    @Override
    public ContentResponse getContentByType(com.netflix.streaming.platform.model.MediaType type, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Content> pageContent = contentRepository.findByContentType(type, pageDetails);

        return buildContentResponse(pageContent);
    }

    // ── Shared helper: turns a Page<Content> into a ContentResponse ──────────
    private ContentResponse buildContentResponse(Page<Content> pageContent) {
        List<ContentDTO> contentDTOs = pageContent.getContent().stream()
                .map(content -> {
                    ContentDTO dto = modelMapper.map(content, ContentDTO.class);
                    dto.setThumbnailUrl(constructImageUrl(content.getThumbnailUrl()));
                    dto.setBannerUrl(constructImageUrl(content.getBannerUrl()));
                    return dto;
                })
                .toList();

        ContentResponse contentResponse = new ContentResponse();
        contentResponse.setContent(contentDTOs);
        contentResponse.setPageNumber(pageContent.getNumber());
        contentResponse.setPageSize(pageContent.getSize());
        contentResponse.setTotalElements(pageContent.getTotalElements());
        contentResponse.setTotalPages(pageContent.getTotalPages());
        contentResponse.setLastPage(pageContent.isLast());
        return contentResponse;
    }


    @Override
    public ContentDTO addStandaloneMovie(ContentDTO contentDTO, MultipartFile poster, MultipartFile banner, MultipartFile videoFile) throws Exception {

        // 1. Map the incoming DTO to a raw Database Entity
        Content content = modelMapper.map(contentDTO, Content.class);
        content.setContentType(com.netflix.streaming.platform.model.MediaType.MOVIE);
        content.setActive(true); // Always active by default

        // 2. Upload Images to Cloudinary (or local depending on your FileService)
        if (poster != null && !poster.isEmpty()) {
            String posterUrl = fileService.uploadImage("posters", poster);
            content.setThumbnailUrl(posterUrl);
        }

        if (banner != null && !banner.isEmpty()) {
            String bannerUrl = fileService.uploadImage("banners", banner);
            content.setBannerUrl(bannerUrl);
        }

        // 3. Save to DB first to generate the ID (we need the ID for the video folder!)
        Content savedContent = contentRepository.save(content);

        // 4. The FFmpeg Video Grinder
        if (videoFile != null && !videoFile.isEmpty()) {
            // Save the raw MP4 temporarily
            String tempMp4Path = fileService.uploadVideoLocally("temp_videos", videoFile);

            // Define where the HLS chunks should go (e.g., /videos/movie_1/)
            String hlsOutputDirectory = "C:/Users/Vipul/Videos/PulseStream/movie_" + savedContent.getId();

            // Trigger FFmpeg
            videoProcessingService.generateHlsStream(tempMp4Path, hlsOutputDirectory);

            // Update the database with the .m3u8 master playlist link!
            savedContent.setVideoUrl(hlsOutputDirectory + "/master.m3u8");
            savedContent = contentRepository.save(savedContent);
        }

        // 5. Convert back to safe DTO to return to Postman
        ContentDTO responseDTO = modelMapper.map(savedContent, ContentDTO.class);
        responseDTO.setThumbnailUrl(constructImageUrl(savedContent.getThumbnailUrl()));
        responseDTO.setBannerUrl(constructImageUrl(savedContent.getBannerUrl()));

        return responseDTO;
    }

    @Override
    public ContentDTO createSeriesShell(ContentDTO contentDTO, MultipartFile poster, MultipartFile banner) throws Exception {
        Content content = modelMapper.map(contentDTO, Content.class);
        content.setContentType(com.netflix.streaming.platform.model.MediaType.TV_SHOW); // Set to SERIES
        content.setActive(true);

        // Upload images
        if (poster != null && !poster.isEmpty()) {
            content.setThumbnailUrl(fileService.uploadImage("posters", poster));
        }
        if (banner != null && !banner.isEmpty()) {
            content.setBannerUrl(fileService.uploadImage("banners", banner));
        }

        // Save shell (No video processing here!)
        Content savedContent = contentRepository.save(content);

        ContentDTO responseDTO = modelMapper.map(savedContent, ContentDTO.class);
        responseDTO.setThumbnailUrl(constructImageUrl(savedContent.getThumbnailUrl()));
        responseDTO.setBannerUrl(constructImageUrl(savedContent.getBannerUrl()));
        return responseDTO;
    }

    @Override
    public SeasonDTO addSeasonToSeries(Long seriesId, Integer seasonNumber, String seasonTitle) throws Exception {
        // Find the parent series
        Content series = contentRepository.findById(seriesId)
                .orElseThrow(() -> new RuntimeException("Series not found with id: " + seriesId));

        // Create the season entity
        Season season = new Season();
        season.setSeasonNumber(seasonNumber);
        season.setTitle(seasonTitle);
        season.setContent(series); // Map the relationship

        Season savedSeason = seasonRepository.save(season);

        return modelMapper.map(savedSeason, SeasonDTO.class);
    }


    @Override
    public EpisodeDTO uploadEpisode(Long seasonId, EpisodeDTO episodeDTO, MultipartFile videoFile) throws Exception {
        // Find the parent season
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new RuntimeException("Season not found with id: " + seasonId));

        // Map DTO to Entity
        Episode episode = modelMapper.map(episodeDTO, Episode.class);
        episode.setSeason(season); // Map the relationship

        episode.setVideoUrl("processing_video...");

        // Save Episode FIRST to get its generated ID
        Episode savedEpisode = episodeRepository.save(episode);

        // The FFmpeg Video Grinder for Episodes
        if (videoFile != null && !videoFile.isEmpty()) {

            // 1. Save the raw MP4 temporarily
            String tempMp4Path = fileService.uploadVideoLocally("temp_videos", videoFile);

            // 2. The OS Path (For FFmpeg to do its job on Windows)
            String hlsOutputDirectory = String.format("C:/Users/Vipul/Videos/PulseStream/series_%d/season_%d/ep_%d",
                    season.getContent().getId(), season.getId(), savedEpisode.getId());

            // 🚨 THE FIX: Create the Web URL so React knows where to look!
            String webUrl = String.format("/videos/series_%d/season_%d/ep_%d/master.m3u8",
                    season.getContent().getId(), season.getId(), savedEpisode.getId());

            // 3. Trigger FFmpeg directly! (Using the Windows path)
            videoProcessingService.generateHlsStream(tempMp4Path, hlsOutputDirectory);

            // 4. Update the database (Using the Web path)
            savedEpisode.setVideoUrl(webUrl);
            savedEpisode = episodeRepository.save(savedEpisode);
        }

        return modelMapper.map(savedEpisode, EpisodeDTO.class);
    }

    // --- The Holy Grail Image URL Constructor ---
    private String constructImageUrl(String imageName) {
        if (imageName == null || imageName.trim().isEmpty()) {
            return imageBaseUrl.endsWith("/") ? imageBaseUrl + "default-poster.png" : imageBaseUrl + "/default-poster.png";
        }
        if (imageName.startsWith("http://") || imageName.startsWith("https://")) {
            return imageName;
        }
        return imageBaseUrl.endsWith("/") ? imageBaseUrl + imageName : imageBaseUrl + "/" + imageName;
    }
}
