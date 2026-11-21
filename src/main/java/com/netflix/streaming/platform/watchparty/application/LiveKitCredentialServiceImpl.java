package com.netflix.streaming.platform.watchparty.application;

import com.netflix.streaming.platform.watchparty.config.LiveKitProperties;
import com.netflix.streaming.platform.watchparty.dto.LiveKitTokenDTO;
import io.livekit.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Generates scoped, opaque LiveKit access tokens for admitted room members.
 */
@Service
public class LiveKitCredentialServiceImpl implements LiveKitCredentialService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitCredentialServiceImpl.class);

    private final LiveKitProperties properties;

    public LiveKitCredentialServiceImpl(LiveKitProperties properties) {
        this.properties = properties;
    }

    @Override
    public LiveKitTokenDTO createAccessToken(String roomId, Long userId, String displayName) {
        if (!isConfigured()) {
            throw new IllegalStateException("LiveKit is not configured. Missing LIVEKIT_API_KEY or LIVEKIT_API_SECRET.");
        }

        // Opaque LiveKit room and participant identifiers (never expose email or PII)
        String livekitRoomName = "room-" + roomId;
        String participantIdentity = "user-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8);

        AccessToken token = new AccessToken(properties.getApiKey(), properties.getApiSecret());
        token.setIdentity(participantIdentity);
        if (displayName != null && !displayName.isBlank()) {
            token.setName(displayName);
        }

        // 4 hours TTL (matches maximum room lifetime)
        token.setTtl(4 * 60 * 60 * 1000L);

        // Security grants:
        // - roomJoin: true
        // - roomName: target room only
        // - canPublish: true
        // - canPublishSources: camera and microphone ONLY (strictly no screen-sharing in MVP)
        // - canSubscribe: true
        // - roomAdmin: false
        // - canPublishData: false
        token.addGrants(
                new RoomJoin(true),
                new RoomName(livekitRoomName),
                new CanPublish(true),
                new CanPublishSources(List.of("camera", "microphone")),
                new CanSubscribe(true),
                new RoomAdmin(false),
                new CanPublishData(false)
        );

        String jwt = token.toJwt();

        log.info("Issued LiveKit token for user {} in room {} (identity: {}, sources: camera, microphone)",
                userId, roomId, participantIdentity);

        return LiveKitTokenDTO.builder()
                .token(jwt)
                .livekitUrl(properties.getUrl())
                .roomName(livekitRoomName)
                .participantIdentity(participantIdentity)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return properties != null && properties.isConfigured();
    }
}

