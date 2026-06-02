package com.netflix.streaming.platform.config;

import com.netflix.streaming.platform.model.Role;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Check if the admin already exists
        if (userRepository.findByEmail("admin@pulsestream.com").isEmpty()) {
            User admin = new User();
            admin.setEmail("admin@pulsestream.com");
            admin.setPassword(passwordEncoder.encode("AdminSecret123!")); // Encrypted!
            admin.setRole(Role.ROLE_ADMIN);
            admin.setPlanTier(com.netflix.streaming.platform.model.PlanTier.PREMIUM);
            admin.setActive(true);
            admin.setVerified(true);

            userRepository.save(admin);
            System.out.println("🛡️ Master Admin Account Created: admin@pulsestream.com");
        }
    }
}