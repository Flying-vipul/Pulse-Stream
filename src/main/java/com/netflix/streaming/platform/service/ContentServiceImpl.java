package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.Episode;
import com.netflix.streaming.platform.model.Season;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.payload.ContentDTO;
import com.netflix.streaming.platform.payload.ContentResponse;
import com.netflix.streaming.platform.payload.EpisodeDTO;
import com.netflix.streaming.platform.payload.SeasonDTO;
import com.netflix.streaming.platform.repositories.ContentRepository;
import com.netflix.streaming.platform.repositories.EpisodeRepository;
import com.netflix.streaming.platform.repositories.SeasonRepository;
import com.netflix.streaming.platform.repositories.UserRepository;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContentServiceImpl implements ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentServiceImpl.class);

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

    //  Azure Blob Service — the cloud uploader
    @Autowired
    private AzureBlobService azureBlobService;

    @Autowired
    private UserRepository userRepository;

    @Value("${image.base.url:http://localhost:8080/images/}")
    private String imageBaseUrl;

    //  OS-AGNOSTIC temp base for FFmpeg HLS output
    //    Windows → C:\Users\Vipul\AppData\Local\Temp\PulseStream
    //    Linux   → /tmp/PulseStream
    private static final String LOCAL_HLS_BASE =
            System.getProperty("java.io.tmpdir") + "/PulseStream";


    @Override
    @Transactional(readOnly = true)
    public ContentResponse getAllContent(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Content> pageContent = contentRepository.findAll(pageDetails);

        return buildContentResponse(pageContent);
    }

    @Override
    @Transactional(readOnly = true)
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
        content.setActive(true);

        // 2. Upload poster & banner images → Cloudinary CDN
        if (poster != null && !poster.isEmpty()) {
            content.setThumbnailUrl(fileService.uploadImage("posters", poster));
        }
        if (banner != null && !banner.isEmpty()) {
            content.setBannerUrl(fileService.uploadImage("banners", banner));
        }

        // 3. Save to DB first to get the generated movie ID
        Content savedContent = contentRepository.save(content);

        // 4. ─── VIDEO PIPELINE: FFmpeg → Azure Blob ───────────────────────────
        if (videoFile != null && !videoFile.isEmpty()) {

            // 4a. Write the raw upload to a local temp file so FFmpeg can read it
            String tempMp4Path = fileService.uploadVideoLocally("temp_videos", videoFile);
            log.info(" Temp MP4 saved at: {}", tempMp4Path);

            // 4b. Local folder where FFmpeg will write .ts segments + .m3u8 playlists
            String hlsOutputDirectory = LOCAL_HLS_BASE + "/movie_" + savedContent.getId();
            log.info(" Running FFmpeg → HLS output: {}", hlsOutputDirectory);

            // 4c. Run FFmpeg (multi-quality 1080p/720p/480p HLS transcode)
            videoProcessingService.generateHlsStream(tempMp4Path, hlsOutputDirectory);
            log.info(" FFmpeg done. Uploading HLS chunks to Azure...");

            // 4d.  Upload the ENTIRE HLS folder to Azure Blob Storage
            //     Azure folder structure: videos/<container>/movies/movie_5/master.m3u8
            String azureFolder = "movies/movie_" + savedContent.getId();
            String azureMasterUrl = azureBlobService.uploadHlsFolderToAzure(hlsOutputDirectory, azureFolder);
            log.info("  Azure Master URL: {}", azureMasterUrl);

            // 4e. Persist the Azure HTTPS URL to the database (React will stream from here)
            savedContent.setVideoUrl(azureMasterUrl);
            savedContent = contentRepository.save(savedContent);

            //  Clean up local temp files (disk space freed, video is safe in Azure)
            azureBlobService.cleanupLocalDirectory(hlsOutputDirectory); // Deletes the HLS chunks

            // Safely delete just the single raw MP4 file
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(tempMp4Path));
            } catch (Exception e) {
                log.warn(" Could not delete temp raw MP4 file: {}", e.getMessage());
            }

            log.info(" Local temp files completely cleaned up.");
        }

        // 5. Return the response DTO
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
        // 1. Find the parent season
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new RuntimeException("Season not found with id: " + seasonId));

        // 2. Map DTO → Entity and save to get the generated episode ID
        Episode episode = modelMapper.map(episodeDTO, Episode.class);
        episode.setSeason(season);
        episode.setVideoUrl("uploading_to_azure...");
        Episode savedEpisode = episodeRepository.save(episode);

        // 3. ─── VIDEO PIPELINE: FFmpeg → Azure Blob ───────────────────────────
        if (videoFile != null && !videoFile.isEmpty()) {

            // 3a. Save raw upload to local temp file for FFmpeg to read
            String tempMp4Path = fileService.uploadVideoLocally("temp_videos", videoFile);
            log.info(" Episode temp MP4 saved: {}", tempMp4Path);

            // 3b. Local FFmpeg output folder
            String hlsOutputDirectory = String.format(
                    LOCAL_HLS_BASE + "/series_%d/season_%d/ep_%d",
                    season.getContent().getId(), season.getId(), savedEpisode.getId());
            log.info(" Running FFmpeg → HLS output: {}", hlsOutputDirectory);

            // 3c. Run FFmpeg (multi-quality HLS transcode)
            videoProcessingService.generateHlsStream(tempMp4Path, hlsOutputDirectory);
            log.info(" FFmpeg done. Uploading episode HLS to Azure...");

            // 3d.  Upload ENTIRE HLS folder to Azure Blob Storage
            //     Azure path: series/series_2/season_1/ep_3/master.m3u8
            String azureFolder = String.format("series/series_%d/season_%d/ep_%d",
                    season.getContent().getId(), season.getId(), savedEpisode.getId());
            String azureMasterUrl = azureBlobService.uploadHlsFolderToAzure(hlsOutputDirectory, azureFolder);
            log.info("  Episode Azure URL: {}", azureMasterUrl);

            // 3e. Persist Azure HTTPS URL to the database
            savedEpisode.setVideoUrl(azureMasterUrl);
            savedEpisode = episodeRepository.save(savedEpisode);

            //  Clean up local temp files (disk space freed, video is safe in Azure)
            azureBlobService.cleanupLocalDirectory(hlsOutputDirectory); // Deletes the HLS chunks

            // Safely delete just the single raw MP4 file
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(tempMp4Path));
            } catch (Exception e) {
                log.warn(" Could not delete temp raw MP4 file: {}", e.getMessage());
            }

            log.info("Local temp files completely cleaned up.");
        }

        return modelMapper.map(savedEpisode, EpisodeDTO.class);
    }

    // =========================================================================
    // WATCHLIST (user-level via @ElementCollection on User)
    // SECURITY: identity always derived from JWT (Authentication), never from
    // a client-supplied userId in the URL. This prevents IDOR attacks.
    // =========================================================================

    private User resolveUserFromAuth(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new APIException("Not authenticated.");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", auth.getName()));
    }

    private ContentDTO toWatchlistDTO(Content c) {
        ContentDTO dto = new ContentDTO();
        dto.setId(c.getId());
        dto.setTitle(c.getTitle());
        dto.setDescription(c.getDescription());
        dto.setReleaseYear(c.getReleaseYear());
        dto.setThumbnailUrl(constructImageUrl(c.getThumbnailUrl()));
        dto.setBannerUrl(constructImageUrl(c.getBannerUrl()));
        dto.setContentType(c.getContentType());
        dto.setDurationMinutes(c.getDurationMinutes());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentDTO> getWatchlist(Authentication auth) {
        User user = resolveUserFromAuth(auth);
        Set<Long> ids = user.getWatchlistContentIds();
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return contentRepository.findByIdIn(ids).stream().map(this::toWatchlistDTO).toList();
    }

    @Override
    @Transactional
    public Map<String, Object> addToWatchlist(Authentication auth, Long contentId) {
        User user = resolveUserFromAuth(auth);
        // Verify content exists before adding
        if (!contentRepository.existsById(contentId)) {
            throw new ResourceNotFoundException("Content", "id", contentId);
        }
        user.getWatchlistContentIds().add(contentId);
        userRepository.save(user);
        return Map.of("inWatchlist", true, "contentId", contentId);
    }

    @Override
    @Transactional
    public Map<String, Object> removeFromWatchlist(Authentication auth, Long contentId) {
        User user = resolveUserFromAuth(auth);
        user.getWatchlistContentIds().remove(contentId);
        userRepository.save(user);
        return Map.of("inWatchlist", false, "contentId", contentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> checkWatchlist(Authentication auth, Long contentId) {
        User user = resolveUserFromAuth(auth);
        boolean inList = user.getWatchlistContentIds().contains(contentId);
        return Map.of("inWatchlist", inList, "contentId", contentId);
    }

    // --- Image URL constructor ---
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

