package com.netflix.streaming.platform.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "content")
@Getter
@Setter
@NoArgsConstructor
// This is the magic annotation that solves the N+1 problem later!
@NamedEntityGraph(name = "Content.genres", attributeNodes = @NamedAttributeNode("genres"))
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private MediaType contentType;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "banner_url")
    private String bannerUrl;

    // Only populated if it's a MOVIE
    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "is_active")
    private boolean isActive = true;

    // The Many-to-Many bridge to Genres
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "content_genres",
            joinColumns = @JoinColumn(name = "content_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres = new HashSet<>();

    // Only populated if it's a TV_SHOW
    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Season> seasons = new ArrayList<>();




}