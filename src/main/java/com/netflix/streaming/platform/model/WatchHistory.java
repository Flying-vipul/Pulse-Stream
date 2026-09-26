package com.netflix.streaming.platform.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(
    name = "watch_history",
    uniqueConstraints = {
        @UniqueConstraint(
            name         = "uq_watch_history_profile_content_episode",
            columnNames  = {"profile_id", "content_id", "episode_id"}
        )
    },
    indexes = {
        @Index(name = "idx_wh_profile_id",      columnList = "profile_id"),
        @Index(name = "idx_wh_completed",        columnList = "profile_id, is_completed"),
        @Index(name = "idx_wh_last_watched_at",  columnList = "last_watched_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    private Episode episode;

    @Column(name = "stopped_at_seconds", nullable = false)
    private int stoppedAtSeconds = 0;

    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted = false;

    @UpdateTimestamp
    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;
}
