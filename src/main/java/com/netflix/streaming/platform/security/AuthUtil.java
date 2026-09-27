package com.netflix.streaming.platform.security;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.ProfileRepository;
import com.netflix.streaming.platform.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    public User loggedInUser() {
        return requireAuthenticatedUser(SecurityContextHolder.getContext().getAuthentication());
    }

    public User requireAuthenticatedUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new APIException("Not authenticated.");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    public Profile requireOwnedProfile(Long profileId, Authentication auth) {
        User user = requireAuthenticatedUser(auth);
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", "id", profileId));
        
        if (!profile.getUser().getId().equals(user.getId())) {
            throw new APIException("Access denied: profile does not belong to the authenticated user.");
        }
        return profile;
    }
}