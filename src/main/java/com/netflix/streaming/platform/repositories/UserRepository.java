package com.netflix.streaming.platform.repositories;

import com.netflix.streaming.platform.model.PlanTier;
import com.netflix.streaming.platform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 1. THE ONLY FINDERS WE NEED NOW (Using Email)
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);

    // 2. THE NIGHT-SHIFT JANITOR QUERY (From our Cron Job earlier)
    @Transactional
    @Modifying
    @Query("DELETE FROM User u WHERE u.isVerified = false AND u.otpExpiry < :cutoffTime")
    void deleteUnverifiedUsersOlderThan(LocalDateTime cutoffTime);

    // 3. ADMIN STATS QUERIES
    // Spring Data JPA derives these SQL queries automatically from the method names:
    //   countByIsActiveTrue()  → SELECT COUNT(*) FROM users WHERE is_active = true
    //   countByPlanTierNot()   → SELECT COUNT(*) FROM users WHERE plan_tier != :planTier
    long countByIsActiveTrue();
    long countByPlanTierNot(PlanTier planTier);
}