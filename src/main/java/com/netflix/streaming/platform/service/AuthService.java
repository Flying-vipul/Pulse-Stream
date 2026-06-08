package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.security.request.LoginRequest;
import com.netflix.streaming.platform.security.request.SignupRequest;
import com.netflix.streaming.platform.security.response.MessageResponse;
import com.netflix.streaming.platform.security.response.UserInfoResponse;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {
    UserInfoResponse authenticateUser(LoginRequest loginRequest);
    MessageResponse registerUser(SignupRequest signupRequest);
    boolean verifyOtp(String email, String otp);
    String generateAndSetOtp(String email);
    UserInfoResponse getUserDetails(Authentication authentication);
    UserInfoResponse uploadAvatar(MultipartFile image, Authentication authentication);

    @Transactional
    MessageResponse triggerForgotPassword(String email);

    @Transactional
    MessageResponse resetPassword(String email, String otp, String newPassword);


}