package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.payload.ProfileDTO;
import com.netflix.streaming.platform.payload.ProfileResponse;
import com.netflix.streaming.platform.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/profiles")
public class ProfileController {

    // 🛡️ Injecting the Interface, fully decoupled
    @Autowired
    private ProfileService profileService;

    // 🛡️ Returns ProfileDTO
    @PostMapping("/create")
    public ResponseEntity<ProfileDTO> createProfile(
            @PathVariable Long userId,
            @RequestParam String profileName) {

        ProfileDTO newProfile = profileService.createProfile(userId, profileName);
        return ResponseEntity.ok(newProfile);
    }

    // 🛡️ CHANGED: Returns ProfileResponse
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfiles(@PathVariable Long userId) {
        return ResponseEntity.ok(profileService.getUserProfiles(userId));
    }
}