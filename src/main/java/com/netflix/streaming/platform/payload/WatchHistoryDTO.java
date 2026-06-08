package com.netflix.streaming.platform.payload;

public class WatchHistoryDTO {

    private Long contentId;
    private int lastWatchedSeconds;

    // Extra fields so the frontend can render Continue Watching cards
    // without making additional API calls
    private String title;
    private String thumbnailUrl;
    private Long episodeId;       // null for movies
    private String episodeTitle;  // null for movies
    private Integer totalDurationSeconds; // for progress bar %

    public WatchHistoryDTO() {}

    // Getters and Setters
    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }

    public int getLastWatchedSeconds() { return lastWatchedSeconds; }
    public void setLastWatchedSeconds(int lastWatchedSeconds) { this.lastWatchedSeconds = lastWatchedSeconds; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public Long getEpisodeId() { return episodeId; }
    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }

    public String getEpisodeTitle() { return episodeTitle; }
    public void setEpisodeTitle(String episodeTitle) { this.episodeTitle = episodeTitle; }

    public Integer getTotalDurationSeconds() { return totalDurationSeconds; }
    public void setTotalDurationSeconds(Integer totalDurationSeconds) { this.totalDurationSeconds = totalDurationSeconds; }
}