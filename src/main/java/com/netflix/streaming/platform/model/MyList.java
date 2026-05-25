package com.netflix.streaming.platform.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "my_list")
@Getter
@Setter
@NoArgsConstructor
public class MyList {

    @EmbeddedId
    private MyListId id = new MyListId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("profileId") // Plugs into MyListId.profileId
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("contentId") // Plugs into MyListId.contentId
    @JoinColumn(name = "content_id")
    private Content content;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false)
    private LocalDateTime addedAt;
}