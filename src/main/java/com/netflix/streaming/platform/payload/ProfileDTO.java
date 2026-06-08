package com.netflix.streaming.platform.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDTO {
    private Long id;
    private String name;

    // I want to  can add an 'avatarUrl' here later if you integrate Cloudinary for profile pics!
}