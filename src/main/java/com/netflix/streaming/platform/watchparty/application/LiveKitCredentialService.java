package com.netflix.streaming.platform.watchparty.application;

import com.netflix.streaming.platform.watchparty.dto.LiveKitTokenDTO;

/**
 * Service contract for generating scoped LiveKit WebRTC access tokens.
 */
public interface LiveKitCredentialService {

    /**
     * Issues an ephemeral LiveKit access token with strict media-only permissions
     * (camera & microphone publish, subscribe, no admin, no screen share).
     *
     * @param roomId PulseStream Watch Party room ID
     * @param userId PulseStream user ID (used to create opaque identity)
     * @param displayName Participant display name
     * @return Connection details with signed JWT
     */
    LiveKitTokenDTO createAccessToken(String roomId, Long userId, String displayName);

    /**
     * Checks if LiveKit credentials (API key and secret) are configured.
     */
    boolean isConfigured();
}

