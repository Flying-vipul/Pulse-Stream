package com.netflix.streaming.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email")
        },
        indexes = {
                @Index(name = "idx_unverified_expiry", columnList = "is_verified, otp_expiry")
        })
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🛡️ THE FIX: This forces Spring to block the request if name is missing!
    @NotBlank(message = "Name cannot be blank")
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    // 🛡️ FIX 2: The password field has been completely eradicated.
    // Authentication is now 100% OTP driven.

    // Defaults  NONE to protect your revenue
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false)
    private PlanTier planTier = PlanTier.NONE;

    @Column(name = "is_active")
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Profile> profiles = new ArrayList<>();

    // ── PERSISTENT WATCHLIST ──────────────────────────────────────────────────
    // Stored in a join table `user_watchlist` (auto-created by Hibernate).
    // Each row is (user_id, content_id). Survives server restarts.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_watchlist", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "content_id")
    private Set<Long> watchlistContentIds = new HashSet<>();


    // ==========================================
    // ADVANCED SECURITY & OTP FIELDS
    // ==========================================

    @JsonIgnore
    @Column(length = 6)
    private String otp;

    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts = 0;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.ROLE_USER;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    // Update your constructor to include the password
    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.planTier = PlanTier.NONE;
        this.role = Role.ROLE_USER;
    }


}