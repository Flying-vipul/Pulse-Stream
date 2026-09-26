package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.payload.ProfileDTO;
import com.netflix.streaming.platform.payload.ProfileResponse;
import org.springframework.security.core.Authentication;

public interface ProfileService {
    /**
     * Creates a new profile for the currently authenticated user.
     * Enforces a maximum of 4 profiles per account.
     *
     * @param profileName name for the new profile
     * @param auth        the current request''s authentication (derived from JWT, never from URL)
     */
    ProfileDTO createProfile(String profileName, Authentication auth);

    /**
     * Returns all profiles belonging to the currently authenticated user.
     *
     * @param auth the current request''s authentication (derived from JWT, never from URL)
     */
    ProfileResponse getUserProfiles(Authentication auth);
}
