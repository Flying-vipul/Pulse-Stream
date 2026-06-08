package com.netflix.streaming.platform.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {

    private List<ProfileDTO> profiles;
    private int totalProfiles;
    private int maxProfilesAllowed;
    private boolean canCreateMore;

}