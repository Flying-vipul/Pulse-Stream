package com.netflix.streaming.platform.watchparty.application;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.watchparty.config.WatchPartyProperties;
import com.netflix.streaming.platform.watchparty.domain.WatchRoom;
import com.netflix.streaming.platform.watchparty.infrastructure.WatchRoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Handles cryptographically secure invitation token generation, SHA-256 hashing,
 * constant-time verification, and rotation.
 */
@Service
public class InvitationService {

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private WatchRoomRepository watchRoomRepository;

    @Autowired
    private WatchPartyProperties properties;

    /**
     * Generates at least 256 bits (32 bytes) of cryptographically secure randomness
     * formatted as a URL-safe Base64 string.
     */
    public String generateRawToken() {
        byte[] bytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Computes the SHA-256 hex hash of the raw token.
     * Raw tokens are never logged or stored in Redis.
     */
    public String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new APIException("Invitation token cannot be empty.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Validates raw token against stored hash using constant-time comparison
     * to prevent timing attacks.
     */
    public boolean verifyToken(String rawToken, String expectedHash) {
        if (rawToken == null || expectedHash == null) {
            return false;
        }
        String computedHash = hashToken(rawToken);
        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Links a new raw token to the room in Redis with appropriate TTL.
     * Returns the raw token to be delivered to the caller exactly once.
     */
    public String createAndLinkInvitation(WatchRoom room) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        room.setInvitationTokenHash(tokenHash);

        Duration ttl = Duration.ofHours(properties.getInvitationExpirationHours());
        watchRoomRepository.linkInviteTokenHash(tokenHash, room.getRoomId(), ttl);

        return rawToken;
    }

    /**
     * Rotates invitation token: invalidates old token hash and links a new one.
     */
    public String rotateInvitation(WatchRoom room) {
        // Unlink previous hash
        if (room.getInvitationTokenHash() != null) {
            watchRoomRepository.unlinkInviteTokenHash(room.getInvitationTokenHash());
        }

        return createAndLinkInvitation(room);
    }
}

