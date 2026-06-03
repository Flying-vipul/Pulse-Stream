package com.netflix.streaming.platform.repositories;

import com.netflix.streaming.platform.model.Content;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long> {

    // The EntityGraph tells Hibernate: "When you fetch Content, grab the genres immediately!"
    @EntityGraph(attributePaths = {"genres"})
    Page<Content> findAll(Pageable pageable);
}