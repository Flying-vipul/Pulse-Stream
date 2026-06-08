package com.netflix.streaming.platform.security.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    private Long id;
    private String jwtToken;
    private String name;
    private String email;
    private String planTier;
    private String role;

}