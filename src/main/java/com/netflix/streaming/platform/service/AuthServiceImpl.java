package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl {



    @Autowired
    private UserRepository userRepository;

    // Cryptographically Secure Random Number Generator
    private static final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String generateAndSetOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // DEFENSE LAYER 1: Stop hackers from spamming "Resend OTP" if locked out
        if (user.getAccountLockedUntil() != null && LocalDateTime.now().isBefore(user.getAccountLockedUntil())) {
            long minutesLeft = Duration.between(LocalDateTime.now(), user.getAccountLockedUntil()).toMinutes();
            throw new RuntimeException("Account is locked due to multiple failed attempts. Try again in " + minutesLeft + " minutes.");
        }

        // Generate a true, unguessable 6-digit number
        int otpNum = 100000 + secureRandom.nextInt(900000);
        String generatedOtp = String.valueOf(otpNum);

        // Arm the Ticking Time Bomb (5 Minutes)
        user.setOtp(generatedOtp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));

        // Clean the slate (reset attempts and unlock)
        user.setOtpAttempts(0);
        user.setAccountLockedUntil(null);

        userRepository.save(user);

        return generatedOtp;
    }

    @Transactional
    public boolean verifyOtp(String email, String enteredOtp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // DEFENSE LAYER 2: Is the account currently locked?
        if (user.getAccountLockedUntil() != null && LocalDateTime.now().isBefore(user.getAccountLockedUntil())) {
            throw new RuntimeException("Account is locked. Please wait until the cooldown period ends.");
        }

        // DEFENSE LAYER 3: Was an OTP requested, or has it expired?
        if (user.getOtp() == null || user.getOtpExpiry() == null || LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new RuntimeException("OTP is missing or expired. Please request a new one.");
        }

        // THE MOMENT OF TRUTH: Does the OTP match?
        if (user.getOtp().equals(enteredOtp)) {
            // SUCCESS! Disarm the bomb and verify
            user.setVerified(true);
            user.setOtp(null);
            user.setOtpExpiry(null);
            user.setOtpAttempts(0);
            user.setAccountLockedUntil(null);

            userRepository.save(user);
            return true;
        } else {
            // FAILURE! Increment strike counter
            int currentAttempts = user.getOtpAttempts() + 1;
            user.setOtpAttempts(currentAttempts);

            if (currentAttempts >= 3) {
                // Strike 3: Lock for 15 minutes and destroy the OTP
                user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(15));
                user.setOtp(null);
                user.setOtpExpiry(null);
                userRepository.save(user);

                throw new RuntimeException("Maximum attempts reached. Account locked for 15 minutes for your security.");
            }

            userRepository.save(user);
            throw new RuntimeException("Invalid OTP. You have " + (3 - currentAttempts) + " attempt(s) left.");
        }
    }
}