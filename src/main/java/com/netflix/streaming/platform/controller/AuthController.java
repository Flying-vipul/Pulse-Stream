package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.payload.OtpVerificationRequest;
import com.netflix.streaming.platform.payload.PasswordResetRequest;
import com.netflix.streaming.platform.security.request.LoginRequest;
import com.netflix.streaming.platform.security.request.SignupRequest;
import com.netflix.streaming.platform.security.response.MessageResponse;
import com.netflix.streaming.platform.security.response.UserInfoResponse;
import com.netflix.streaming.platform.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            UserInfoResponse response = authService.authenticateUser(loginRequest);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        try {
            MessageResponse response = authService.registerUser(signupRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        try {
            boolean isVerified = authService.verifyOtp(request.getEmail(), request.getOtp());
            if (isVerified) {
                return ResponseEntity.ok(new MessageResponse("Account successfully verified! Grab some popcorn and log in."));
            }
            return ResponseEntity.badRequest().body(new MessageResponse("Verification failed. Invalid or expired OTP."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @GetMapping("/user")
    public ResponseEntity<UserInfoResponse> getUserDetails(Authentication authentication) {
        UserInfoResponse response = authService.getUserDetails(authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signout")
    public ResponseEntity<?> signOutUser() {
        return ResponseEntity.ok(new MessageResponse("You've been successfully signed out of PulseStream."));
    }

    @PutMapping("/profile/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("image") MultipartFile image) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UserInfoResponse response = authService.uploadAvatar(image, authentication);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new MessageResponse(e.getMessage()));
        }
    }

    // You can just accept the email as a query parameter for the trigger
    @PostMapping("/forgot-password/trigger")
    public ResponseEntity<?> triggerForgotPassword(@RequestParam String email) {
        try {
            MessageResponse response = authService.triggerForgotPassword(email);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    // You will need a quick DTO for this one (e.g., PasswordResetRequest containing email, otp, newPassword)
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> resetPassword(@RequestBody PasswordResetRequest request) {
        try {
            MessageResponse response = authService.resetPassword(
                    request.getEmail(), request.getOtp(), request.getNewPassword()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
}