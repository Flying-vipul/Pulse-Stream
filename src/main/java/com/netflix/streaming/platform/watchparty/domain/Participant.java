package com.netflix.streaming.platform.watchparty.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admitted participant in a Watch Party room.
 * User ID is the authoritative security identity; Profile is strictly for presentation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Participant {

    /** PulseStream User ID (authoritative security identity) */
    private Long userId;

    /** Profile ID selected by user for display presentation */
    private Long profileId;

    /** Display name (from profile or user) */
    private String displayName;

    /** Avatar URL for participant tile */
    private String avatarUrl;

    /** Current membership state */
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.ACTIVE;

    /** Whether this participant created the room */
    private boolean isHost;

    /** Opaque participant ID issued for LiveKit WebRTC (never uses email) */
    private String livekitParticipantId;

    /** Timestamp when first admitted to room */
    private long joinedAtMs;

    /** Timestamp of last heartbeat or message */
    private long lastSeenAtMs;

    /** Timestamp when disconnected unexpectedly (null if ACTIVE) */
    private Long disconnectAtMs;

    /** Active WebSocket session identifier */
    private String wsSessionId;
}
