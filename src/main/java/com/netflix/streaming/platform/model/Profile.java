package com.netflix.streaming.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "profiles",
    indexes = {
        @Index(name = "idx_profiles_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"user"})
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "profile_name", nullable = false)
    private String profileName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "maturity_level")
    private MaturityRating maturityLevel = MaturityRating.ADULTS;
}
