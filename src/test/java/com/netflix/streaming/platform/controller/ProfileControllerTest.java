package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.payload.ProfileDTO;
import com.netflix.streaming.platform.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProfileControllerTest {

    @InjectMocks
    private ProfileController profileController;

    @Mock
    private ProfileService profileService;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateProfile() {
        ProfileDTO profileDTO = new ProfileDTO(1L, "Kids");
        when(profileService.createProfile("Kids", authentication)).thenReturn(profileDTO);

        ResponseEntity<ProfileDTO> response = profileController.createProfile("Kids", authentication);
        
        assertEquals(201, response.getStatusCodeValue());
        assertEquals(profileDTO, response.getBody());
    }

    @Test
    void testCreateProfile_MaxProfilesException() {
        when(profileService.createProfile("Kids", authentication))
            .thenThrow(new APIException("Maximum of 4 profiles allowed per account."));

        assertThrows(APIException.class, () -> profileController.createProfile("Kids", authentication));
    }
}
