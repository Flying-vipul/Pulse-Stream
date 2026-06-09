package com.netflix.streaming.platform.repositories;

import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long> {

    // The EntityGraph tells Hibernate: "When you fetch Content, grab the genres immediately!"
    @EntityGraph(attributePaths = {"genres"})
    Page<Content> findAll(Pageable pageable);

    // Filter by type: MOVIE or SERIES
    @EntityGraph(attributePaths = {"genres"})
    Page<Content> findByContentType(MediaType contentType, Pageable pageable);

    // Fetch watchlist items by a set of IDs
    @EntityGraph(attributePaths = {"genres"})
    List<Content> findByIdIn(Set<Long> ids);
}