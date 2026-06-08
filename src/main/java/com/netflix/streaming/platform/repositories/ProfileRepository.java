package com.netflix.streaming.platform.repositories;

import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {
    List<Profile> findByUser(User user);
    // Spring Boot automatically gives you findById() just by extending JpaRepository!
}