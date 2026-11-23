package com.netflix.streaming.platform.watchparty.domain;

/**
 * Membership status of a participant in a Watch Party room.
 */
public enum ParticipantStatus {
    /** Connected and actively receiving/sending playback events */
    ACTIVE,

    /** Disconnected unexpectedly; slot is retained during 90-second grace period */
    RECONNECTING,

    /** Intentionally left or grace period expired; slot is released */
    LEFT
}
