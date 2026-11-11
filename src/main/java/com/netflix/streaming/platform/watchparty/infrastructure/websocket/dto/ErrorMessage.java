package com.netflix.streaming.platform.watchparty.infrastructure.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message payload sent to private user error queue /user/queue/watch-party-errors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorMessage {

    private String code;
    private String message;
    private long timestamp;

    public static ErrorMessage of(String code, String message) {
        return ErrorMessage.builder()
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}

