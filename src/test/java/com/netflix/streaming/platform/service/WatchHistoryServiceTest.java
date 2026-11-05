package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.Profile;
import com.netflix.streaming.platform.model.WatchHistory;
import com.netflix.streaming.platform.payload.HeartbeatRequest;
import com.netflix.streaming.platform.repositories.ContentRepository;
import com.netflix.streaming.platform.repositories.WatchHistoryRepository;
import com.netflix.streaming.platform.security.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WatchHistoryServiceTest {

    @InjectMocks
    private WatchHistoryServiceImpl watchHistoryService;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private AuthUtil authUtil;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testUpdateProgress_InvalidProgress() {
        HeartbeatRequest req = new HeartbeatRequest(1L, null, -5);
        assertThrows(APIException.class, () -> watchHistoryService.updateProgress(authentication, 1L, req));
    }

    @Test
    void testUpdateProgress_ProfileOwnershipDenied() {
        when(authUtil.requireOwnedProfile(any(), eq(authentication)))
            .thenThrow(new APIException("Access denied: profile does not belong to the authenticated user."));

        HeartbeatRequest req = new HeartbeatRequest(1L, null, 10);
        assertThrows(APIException.class, () -> watchHistoryService.updateProgress(authentication, 1L, req));
    }

    @Test
    void testUpdateProgress_Success() {
        Profile profile = new Profile();
        profile.setId(1L);

        Content content = new Content();
        content.setId(1L);
        content.setDurationMinutes(120);

        WatchHistory existingHistory = new WatchHistory();
        existingHistory.setStoppedAtSeconds(100);
    }
}

