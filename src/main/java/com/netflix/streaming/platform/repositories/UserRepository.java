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

    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);

    @Transactional
    @Modifying
    @Query("DELETE FROM User u WHERE u.isVerified = false AND u.otpExpiry < :cutoffTime")
    void deleteUnverifiedUsersOlderThan(LocalDateTime cutoffTime);

    long countByIsActiveTrue();
    long countByPlanTierNot(PlanTier planTier);
}
