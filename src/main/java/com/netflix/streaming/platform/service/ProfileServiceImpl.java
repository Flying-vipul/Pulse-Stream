package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.payload.ProfileDTO;
import com.netflix.streaming.platform.payload.ProfileResponse;
import com.netflix.streaming.platform.repositories.ProfileRepository;
import com.netflix.streaming.platform.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileServiceImpl implements ProfileService {

    // 🛡️ ONLY Repositories get @Autowired
    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public ProfileDTO createProfile(Long userId, String profileName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setProfileName(profileName);
        Profile savedProfile = profileRepository.save(profile);

        return new ProfileDTO(savedProfile.getId(), savedProfile.getProfileName());
    }

    @Override
    public ProfileResponse getUserProfiles(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        List<Profile> profiles = profileRepository.findByUser(user);

        // 🛡️ The variable is declared locally inside the method here!
        List<ProfileDTO> profileDTOList = profiles.stream()
                .map(profile -> new ProfileDTO(profile.getId(), profile.getProfileName()))
                .toList();

        int maxAllowed = 4;
        boolean canCreateMore = profileDTOList.size() < maxAllowed;

        return new ProfileResponse(
                profileDTOList,
                profileDTOList.size(),
                maxAllowed,
                canCreateMore
        );
    }
}