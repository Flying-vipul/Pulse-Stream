package com.netflix.streaming.platform.security.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

    // 🛡️ The Correct Validation Annotation for DTOs!
    @NotBlank(message = "Name cannot be blank")
    private String name;

    @NotBlank(message = "Email cannot be blank")
    @Size(max = 50, message = "Email cannot exceed 50 characters")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 6, max = 40, message = "Password must be between 6 and 40 characters")
    private String password;

    // 🛡️ PlanTier has been completely eradicated from this file.
    // The backend AuthServiceImpl will automatically force PlanTier.NONE!
}