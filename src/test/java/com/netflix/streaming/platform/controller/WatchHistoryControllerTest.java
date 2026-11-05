package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.payload.HeartbeatRequest;
import com.netflix.streaming.platform.service.WatchHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WatchHistoryControllerTest {

    @InjectMocks
    private WatchHistoryController watchHistoryController;

    @Mock
    private WatchHistoryService watchHistoryService;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveProgress() {
        Long profileId = 10L;
        HeartbeatRequest request = new HeartbeatRequest(1L, null, 300);
        
        ResponseEntity<String> response = watchHistoryController.saveProgress(authentication, profileId, request);

        assertEquals(200, response.getStatusCodeValue());
        verify(watchHistoryService, times(1)).updateProgress(authentication, profileId, request);
    }
}

