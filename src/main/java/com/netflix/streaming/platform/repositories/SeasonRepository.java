package com.netflix.streaming.platform.repositories;

import com.netflix.streaming.platform.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeasonRepository extends JpaRepository<Season, Long> {

    @Query("SELECT DISTINCT s FROM Season s LEFT JOIN FETCH s.episodes WHERE s.content.id = :contentId ORDER BY s.seasonNumber ASC")
    List<Season> findByContentIdWithEpisodes(@Param("contentId") Long contentId);
}