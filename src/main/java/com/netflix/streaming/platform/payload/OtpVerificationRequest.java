package com.netflix.streaming.platform.payload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpVerificationRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String otp;
}