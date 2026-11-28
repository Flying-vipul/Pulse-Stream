package com.netflix.streaming.platform.watchparty.domain;

/**
 * Commands that can be issued by the room host.
 */
public enum PlaybackCommandType {
    PLAY,
    PAUSE,
    SEEK
}
