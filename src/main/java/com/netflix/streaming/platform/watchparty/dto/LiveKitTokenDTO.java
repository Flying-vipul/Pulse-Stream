package com.netflix.streaming.platform.watchparty.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LiveKit WebRTC connection payload issued to admitted room participants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveKitTokenDTO {

    /** Signed LiveKit JWT access token */
    private String token;

    /** LiveKit server URL */
    private String livekitUrl;

    /** Opaque room identifier within LiveKit */
    private String roomName;

    /** Opaque participant identity within LiveKit (never contains email or PII) */
    private String participantIdentity;
}

