package com.netflix.streaming.platform.repositories;

import com.netflix.streaming.platform.model.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    // Empty interface. JPA handles the rest.
}