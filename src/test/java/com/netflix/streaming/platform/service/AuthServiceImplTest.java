package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.security.request.SignupRequest;
import com.netflix.streaming.platform.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegisterUser_DuplicateEmail() {
        SignupRequest request = new SignupRequest();
        request.setEmail("test@example.com");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(APIException.class, () -> authService.registerUser(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testVerifyOtp_InvalidOtp() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setOtp("123456");
        user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(5));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThrows(APIException.class, () -> authService.verifyOtp("test@example.com", "654321"));
    }

    @Test
    void testVerifyOtp_ExpiredOtp() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setOtp("123456");
        user.setOtpExpiry(java.time.LocalDateTime.now().minusMinutes(5)); // Expired

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThrows(APIException.class, () -> authService.verifyOtp("test@example.com", "123456"));
    }
}
