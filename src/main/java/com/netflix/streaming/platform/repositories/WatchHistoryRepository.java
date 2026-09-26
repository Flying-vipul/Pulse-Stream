package com.netflix.streaming.platform.repositories;

import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.Episode;
import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.WatchHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    Optional<WatchHistory> findByProfileAndContentAndEpisode(
            Profile profile, Content content, Episode episode);

    @EntityGraph(attributePaths = {"content", "episode"})
    List<WatchHistory> findByProfileAndIsCompletedFalseOrderByLastWatchedAtDesc(Profile profile);
}
