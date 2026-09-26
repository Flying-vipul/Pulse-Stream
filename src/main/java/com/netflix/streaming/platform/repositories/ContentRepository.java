package com.netflix.streaming.platform.repositories;

import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.MediaType;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long> {

    @NotNull
    Page<Content> findAll(
            @NotNull
            Pageable pageable
    );

    Page<Content> findByContentType(MediaType contentType, Pageable pageable);

    List<Content> findByIdIn(Set<Long> ids);


    @EntityGraph(attributePaths = {"genres"})
    @Query("SELECT c FROM Content c WHERE c.id = :id")
    Optional<Content> findByIdWithGenres(@Param("id") Long id);
}
