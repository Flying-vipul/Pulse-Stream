package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.payload.ProfileDTO;
import com.netflix.streaming.platform.payload.ProfileResponse;

import java.util.List;

public interface ProfileService {
    ProfileDTO createProfile(Long userId, String profileName);
    ProfileResponse getUserProfiles(Long userId);
}