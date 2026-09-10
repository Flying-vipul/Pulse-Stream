package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.model.PlanTier;
import com.netflix.streaming.platform.model.Role;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.UserRepository;
import com.netflix.streaming.platform.security.jwt.JwtUtils;
import com.netflix.streaming.platform.security.request.LoginRequest;
import com.netflix.streaming.platform.security.request.SignupRequest;
import com.netflix.streaming.platform.security.response.MessageResponse;
import com.netflix.streaming.platform.security.response.UserInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    // Cryptographically secure RNG — static so the seed entropy is not wasted on every call
    private static final SecureRandom secureRandom = new SecureRandom();

    @Autowired private UserRepository userRepository;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private PasswordEncoder encoder;
    @Autowired private EmailService emailService;
    @Autowired private FileService fileService;

    @Value("${image.base.url:http://localhost:8080/images/}")
    private String imageBaseUrl;

    // =========================================================================
    // LOGIN
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public UserInfoResponse authenticateUser(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new APIException("Invalid email or password."));

        if (!user.isVerified()) {
            throw new APIException("Please verify your email with the OTP before logging in.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        if (userDetails == null) {
            throw new APIException("Authentication failed — could not load user details.");
        }

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

    // =========================================================================
    // SIGNUP
    // =========================================================================

    @Override
    @Transactional
    public MessageResponse registerUser(SignupRequest signupRequest) {
        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new APIException("An account with this email already exists.");
        }

        User user = new User();
        user.setName(signupRequest.getName());
        user.setEmail(signupRequest.getEmail());
        user.setPassword(encoder.encode(signupRequest.getPassword()));
        user.setPlanTier(PlanTier.NONE);  // Always forced; never trust client input
        user.setRole(Role.ROLE_USER);     // Always forced; never trust client input

        userRepository.save(user);

        try {
            String generatedOtp = generateAndSetOtp(user.getEmail());
            emailService.sendOtpEmail(user.getEmail(), generatedOtp);
        } catch (Exception e) {
            // Account is created but email failed — surface a clear error.
            // The user can request a resend OTP.
            logger.error("OTP email send failed for new user registration: {}", e.getClass().getSimpleName());
            throw new APIException("Account created, but failed to send verification email. Please use Resend OTP.");
        }

        return new MessageResponse("Welcome to PulseStream! Please check your email for your 6-digit verification code.");
    }

    // =========================================================================
    // GET CURRENT USER DETAILS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public UserInfoResponse getUserDetails(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new APIException("Not authenticated.");
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User userRecord = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new APIException("Authenticated user not found in database."));

        return new UserInfoResponse(
                userDetails.getId(),
                null,   // Never return a new JWT on a simple GET — client already has one
                userDetails.getName(),
                userDetails.getUsername(),
                userRecord.getPlanTier().name(),
                userRecord.getRole().name()
        );
    }

    // =========================================================================
    // AVATAR UPLOAD
    // =========================================================================

    @Override
    @Transactional
    public UserInfoResponse uploadAvatar(MultipartFile image, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new APIException("Not authenticated.");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new APIException("User not found."));

        try {
            // fileService.uploadImage returns the stored file name/path
            String uploadedFileName = fileService.uploadImage("avatars", image);
            // Note: avatar URL is not persisted on User entity by default — extend if needed
            userRepository.save(user);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            return new UserInfoResponse(
                    userDetails.getId(),
                    null,
                    userDetails.getName(),
                    userDetails.getUsername(),
                    user.getPlanTier().name(),
                    user.getRole().name()
            );
        } catch (Exception e) {
            logger.error("Avatar upload failed: {}", e.getClass().getSimpleName());
            throw new APIException("Avatar upload failed. Please try again.");
        }
    }

    // =========================================================================
    // OTP: GENERATE
    // Shared by signup verification and forgot-password flows.
    // =========================================================================

    @Override
    @Transactional
    public String generateAndSetOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new APIException("User not found."));

        // Block resend if account is currently locked
        if (user.getAccountLockedUntil() != null && LocalDateTime.now().isBefore(user.getAccountLockedUntil())) {
            long minutesLeft = Duration.between(LocalDateTime.now(), user.getAccountLockedUntil()).toMinutes() + 1;
            throw new APIException("Account is locked due to multiple failed attempts. Try again in " + minutesLeft + " minutes.");
        }

        int otpNum = 100000 + secureRandom.nextInt(900000);
        String generatedOtp = String.valueOf(otpNum);

        // SECURITY: OTP is NOT logged or printed to stdout.
        // It is sent only via the email channel.

        user.setOtp(generatedOtp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        user.setOtpAttempts(0);
        user.setAccountLockedUntil(null);
        userRepository.save(user);

        return generatedOtp;
    }

    // =========================================================================
    // OTP: VERIFY (signup email-verification flow)
    // Sets isVerified = true on success. Do NOT call this from password reset.
    // =========================================================================

    @Override
    @Transactional
    public boolean verifyOtp(String email, String enteredOtp) {
        return verifyOtpInternal(email, enteredOtp, true);
    }

    // =========================================================================
    // OTP: INTERNAL VERIFIER
    // setVerified=true  → signup verification path
    // setVerified=false → password-reset path (account state not changed)
    // =========================================================================

    @Transactional
    protected boolean verifyOtpInternal(String email, String enteredOtp, boolean setVerified) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new APIException("User not found."));

        if (user.getAccountLockedUntil() != null && LocalDateTime.now().isBefore(user.getAccountLockedUntil())) {
            throw new APIException("Account is locked. Please wait until the cooldown period ends.");
        }

        if (user.getOtp() == null || user.getOtpExpiry() == null || LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new APIException("OTP is missing or expired. Please request a new one.");
        }

        if (user.getOtp().equals(enteredOtp)) {
            // SUCCESS — clear OTP fields
            if (setVerified) {
                user.setVerified(true);
            }
            user.setOtp(null);
            user.setOtpExpiry(null);
            user.setOtpAttempts(0);
            user.setAccountLockedUntil(null);
            userRepository.save(user);
            return true;
        } else {
            // FAILURE — increment attempt counter
            int attempts = user.getOtpAttempts() + 1;
            user.setOtpAttempts(attempts);

            if (attempts >= 3) {
                user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(15));
                user.setOtp(null);
                user.setOtpExpiry(null);
                userRepository.save(user);
                throw new APIException("Maximum attempts reached. Account locked for 15 minutes.");
            }

            userRepository.save(user);
            throw new APIException("Invalid OTP. You have " + (3 - attempts) + " attempt(s) left.");
        }
    }

    // =========================================================================
    // FORGOT PASSWORD: TRIGGER
    // SECURITY: always returns the same message regardless of whether the email
    // exists, to prevent account enumeration via differential responses.
    // =========================================================================

    @Override
    @Transactional
    public MessageResponse triggerForgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            try {
                String generatedOtp = generateAndSetOtp(user.getEmail());
                emailService.sendOtpEmail(user.getEmail(), generatedOtp);
            } catch (Exception e) {
                // Swallow silently — caller always gets the same vague response
                logger.warn("Could not send password reset OTP: {}", e.getClass().getSimpleName());
            }
        });
        // Same message whether email exists or not — prevents account enumeration
        return new MessageResponse("If that email is registered, a reset code has been sent.");
    }

    // =========================================================================
    // FORGOT PASSWORD: RESET
    // Uses verifyOtpInternal with setVerified=false so that password reset
    // does NOT accidentally re-verify an unverified account.
    // =========================================================================

    @Override
    @Transactional
    public MessageResponse resetPassword(String email, String otp, String newPassword) {
        // verifyOtpInternal with setVerified=false: clears OTP but does NOT
        // change isVerified — prevents the reset flow from being used to
        // skip email verification.
        verifyOtpInternal(email, otp, false);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new APIException("User not found."));

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);

        return new MessageResponse("Password successfully reset. You can now log in.");
    }

    // =========================================================================
    // PRIVATE HELPER
    // =========================================================================

    private String constructAvatarUrl(String avatarIdentifier) {
        if (avatarIdentifier == null || avatarIdentifier.trim().isEmpty()) {
            return imageBaseUrl.endsWith("/") ? imageBaseUrl + "default-avatar.png"
                    : imageBaseUrl + "/default-avatar.png";
        }
        if (avatarIdentifier.startsWith("http://") || avatarIdentifier.startsWith("https://")) {
            return avatarIdentifier;
        }
        return imageBaseUrl.endsWith("/") ? imageBaseUrl + avatarIdentifier
                : imageBaseUrl + "/" + avatarIdentifier;
    }
}
