package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.payload.ProfileDTO;
import com.netflix.streaming.platform.payload.ProfileResponse;
import com.netflix.streaming.platform.repositories.ProfileRepository;
import com.netflix.streaming.platform.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProfileServiceImpl implements ProfileService {

    private static final int MAX_PROFILES = 4;

    @Autowired private ProfileRepository profileRepository;
    @Autowired private UserRepository userRepository;

    /**
     * Resolves the authenticated user from the JWT principal.
     * SECURITY: identity is always derived from the token, never from a client-supplied userId.
     */
    private User resolveAuthenticatedUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new APIException("Not authenticated.");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Override
    @Transactional
    public ProfileDTO createProfile(String profileName, Authentication auth) {
        User user = resolveAuthenticatedUser(auth);

        List<Profile> existing = profileRepository.findByUser(user);
        if (existing.size() >= MAX_PROFILES) {
            throw new APIException("Maximum of " + MAX_PROFILES + " profiles allowed per account.");
        }

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setProfileName(profileName);
        Profile saved = profileRepository.save(profile);

        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getUserProfiles(Authentication auth) {
        User user = resolveAuthenticatedUser(auth);

        List<Profile> profiles = profileRepository.findByUser(user);
        List<ProfileDTO> dtos = profiles.stream().map(this::toDTO).toList();

        return new ProfileResponse(dtos, dtos.size(), MAX_PROFILES, dtos.size() < MAX_PROFILES);
    }

    /**
     * Verifies that the given profileId belongs to the authenticated user.
     * Used internally by WatchHistoryService to enforce ownership before
     * reading or mutating watch history.
     *
     * @throws APIException with 403-semantics if the profile does not belong to this user
     */
    public Profile verifyProfileOwnership(Long profileId, Authentication auth) {
        User user = resolveAuthenticatedUser(auth);
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", profileId));

        if (!profile.getUser().getId().equals(user.getId())) {
            throw new APIException("Access denied: profile does not belong to the authenticated user.");
        }
        return profile;
    }

    private ProfileDTO toDTO(Profile profile) {
        return new ProfileDTO(profile.getId(), profile.getProfileName());
    }
}
