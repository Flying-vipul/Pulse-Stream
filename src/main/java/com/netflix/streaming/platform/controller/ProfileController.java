package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.payload.ProfileDTO;
import com.netflix.streaming.platform.payload.ProfileResponse;
import com.netflix.streaming.platform.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Profile management endpoints.
 *
 * <p>SECURITY: All endpoints derive the user identity from the JWT via the
 * injected {@link Authentication} object. No userId path variable is accepted
 * from the client — doing so would create an IDOR vulnerability where User A
 * could read or modify User B''s profiles.
 *
 * <p>Old routes: /api/users/{userId}/profiles  (removed — IDOR risk)
 * <p>New routes: /api/v1/profiles
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    /**
     * Creates a new profile for the authenticated user.
     * POST /api/v1/profiles?profileName=Kids
     */
    @PostMapping
    public ResponseEntity<ProfileDTO> createProfile(
            @RequestParam String profileName,
            Authentication authentication) {

        ProfileDTO newProfile = profileService.createProfile(profileName, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(newProfile);
    }

    /**
     * Returns all profiles belonging to the authenticated user.
     * GET /api/v1/profiles
     */
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfiles(Authentication authentication) {
        return ResponseEntity.ok(profileService.getUserProfiles(authentication));
    }
}
