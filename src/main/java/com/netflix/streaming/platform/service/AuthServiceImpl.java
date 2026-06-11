package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.model.PlanTier;
import com.netflix.streaming.platform.model.Role;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.UserRepository;
import com.netflix.streaming.platform.security.jwt.JwtUtils;
import com.netflix.streaming.platform.security.request.LoginRequest;
import com.netflix.streaming.platform.security.request.SignupRequest;
import com.netflix.streaming.platform.security.response.MessageResponse;
import com.netflix.streaming.platform.security.response.UserInfoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;


    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private FileService fileService;

    @Value("${image.base.url:http://localhost:8080/images/}")
    private String imageBaseUrl;

    @Override
    public UserInfoResponse authenticateUser(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("Error: User not found."));

        if (!user.isVerified()) {
            throw new RuntimeException("Error: Please verify your email with the OTP before logging in.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        assert userDetails != null;
        String jwtToken = jwtUtils.generateJwtToken(userDetails);

        return new UserInfoResponse(
                userDetails.getId(),
                jwtToken,
                userDetails.getName(),
                userDetails.getUsername(),
                user.getPlanTier().name(),
                user.getRole().name()
        );
    }

    @Override
    public MessageResponse registerUser(SignupRequest signupRequest) {
        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new RuntimeException("Error: Email is already in use by another account!");
        }

        // 🛡️ THE FIX: Ignore the frontend's plan request. Hardcode to NONE.
        User user = new User();
        user.setName(signupRequest.getName()); // Make sure 'name' is in your SignupRequest DTO!
        user.setEmail(signupRequest.getEmail());
        user.setPassword(encoder.encode(signupRequest.getPassword()));
        user.setPlanTier(PlanTier.NONE); // Force free tier
        user.setRole(Role.ROLE_USER);

        userRepository.save(user);

        try {
            String generatedOtp = generateAndSetOtp(user.getEmail());
            emailService.sendOtpEmail(user.getEmail(), generatedOtp);
        } catch (Exception e) {
            throw new RuntimeException("Account created, but failed to send verification email.");
        }

        return new MessageResponse("Welcome to PulseStream! Please check your email for your 6-digit verification code.");
    }

    @Override
    public UserInfoResponse getUserDetails(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        assert userDetails != null;
        User userRecord = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new UserInfoResponse(
                userDetails.getId(),
                null,
                userDetails.getName(),
                userDetails.getUsername(),
                userRecord.getPlanTier().name(),
                userRecord.getRole().name()
        );
    }

    @Override
    public UserInfoResponse uploadAvatar(MultipartFile image, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            String uploadedFileName = fileService.uploadImage("avatars", image);
            userRepository.save(user);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            assert userDetails != null;
            return new UserInfoResponse(
                    userDetails.getId(),
                    null,
                    userDetails.getName(),
                    userDetails.getUsername(),
                    user.getPlanTier().name(),
                    user.getRole().name()
            );
        } catch (Exception e) {
            throw new RuntimeException("Avatar upload failed", e);
        }
    }

    // --- Helper Method for Avatar URL Construction ---
    private String constructAvatarUrl(String avatarIdentifier) {
        if (avatarIdentifier == null || avatarIdentifier.trim().isEmpty()) {
            return imageBaseUrl.endsWith("/") ? imageBaseUrl + "default-avatar.png" : imageBaseUrl + "/default-avatar.png";
        }
        if (avatarIdentifier.startsWith("http://") || avatarIdentifier.startsWith("https://")) {
            return avatarIdentifier;
        }
        return imageBaseUrl.endsWith("/") ? imageBaseUrl + avatarIdentifier : imageBaseUrl + "/" + avatarIdentifier;
    }





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
        System.out.println("\n========================================");
        System.out.println("🚨 DEV MODE - OTP FOR " + email + " IS: " + generatedOtp);
        System.out.println("========================================\n");
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

    // ==========================================
    // FORGOT PASSWORD FEATURE
    // ==========================================

    @Transactional
    @Override
    public MessageResponse triggerForgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("If that email exists, a reset code was sent.")); // Vague error for security!

        // Reuse your secure OTP generator!
        String generatedOtp = generateAndSetOtp(user.getEmail());

        // Send the email (assuming your EmailService has a generic message method, or just use sendOtpEmail)
        emailService.sendOtpEmail(user.getEmail(), generatedOtp);

        return new MessageResponse("Password reset code sent to your email.");
    }

    @Transactional
    @Override
    public MessageResponse resetPassword(String email, String otp, String newPassword) {
        // 1. We reuse your existing verifyOtp logic!
        // It already handles expiry, 3-strike lockouts, and sets isVerified = true.
        boolean isOtpValid = verifyOtp(email, otp);

        if (isOtpValid) {
            // 2. If the OTP was correct, find the user and update the password
            User user = userRepository.findByEmail(email).get();
            user.setPassword(encoder.encode(newPassword));
            userRepository.save(user);
            return new MessageResponse("Password successfully reset! You can now log in.");
        }

        throw new RuntimeException("Failed to reset password.");
    }
}