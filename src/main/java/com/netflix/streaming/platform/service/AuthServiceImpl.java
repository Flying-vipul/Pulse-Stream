package com.netflix.streaming.platform.service;

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
                userDetails.getUsername(),
                user.getPlanTier().name(),
                constructAvatarUrl(user.getAvatarUrl())
        );
    }

    @Override
    public MessageResponse registerUser(SignupRequest signupRequest) {
        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new RuntimeException("Error: Email is already in use by another account!");
        }

        User user = new User(
                signupRequest.getEmail(),
                encoder.encode(signupRequest.getPassword()),
                signupRequest.getPlanTier()
        );

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
        User userRecord = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new UserInfoResponse(
                userDetails.getId(),
                null,
                userDetails.getUsername(),
                userRecord.getPlanTier().name(),
                constructAvatarUrl(userRecord.getAvatarUrl())
        );
    }

    @Override
    public UserInfoResponse uploadAvatar(MultipartFile image, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            String uploadedFileName = fileService.uploadImage("avatars", image);
            user.setAvatarUrl(uploadedFileName);
            userRepository.save(user);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            return new UserInfoResponse(
                    userDetails.getId(),
                    null,
                    userDetails.getUsername(),
                    user.getPlanTier().name(),
                    constructAvatarUrl(uploadedFileName)
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