package com.netflix.streaming.platform.security;

import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    @Autowired
    private UserRepository userRepository;

    // This grabs the email from the JWT token and fetches the full User object from Postgres
    public User loggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Spring Security stores the email (or username) in the 'name' field of the authentication object
        assert authentication != null;
        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}