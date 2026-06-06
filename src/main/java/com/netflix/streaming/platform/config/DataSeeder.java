package com.netflix.streaming.platform.config;

import com.netflix.streaming.platform.model.Role;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Pulling admin credentials from application.yml
    @Value("${app.admin.email:admin@pulsestream.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        // Check if the admin already exists
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User admin = new User();

            // 🛡️ THE FIX: Name is now explicitly set to prevent the ConstraintViolationException!
            admin.setName("PulseStream Admin");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword)); // Encrypted!
            admin.setRole(Role.ROLE_ADMIN);
            admin.setPlanTier(com.netflix.streaming.platform.model.PlanTier.PREMIUM);
            admin.setActive(true);
            admin.setVerified(true);

            userRepository.save(admin);
            System.out.println("🛡️ Master Admin Account Created: " + adminEmail);
        }
    }
}