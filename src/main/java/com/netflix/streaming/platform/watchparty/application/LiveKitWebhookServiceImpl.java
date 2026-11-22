package com.netflix.streaming.platform.watchparty.application;

import com.netflix.streaming.platform.watchparty.config.LiveKitProperties;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Service implementation for LiveKit webhook signature verification and idempotent event dispatch.
 */
@Service
public class LiveKitWebhookServiceImpl implements LiveKitWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookServiceImpl.class);
    private static final String DEDUP_KEY_PREFIX = "watchparty:livekit:event:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final LiveKitProperties properties;
    private final StringRedisTemplate stringRedisTemplate;

    public LiveKitWebhookServiceImpl(LiveKitProperties properties,
                                     @Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public WebhookEvent verifyAndParse(String rawBody, String authHeader) {
        if (!properties.isConfigured()) {
            log.error("LiveKit webhook rejected: LiveKit is not configured with API key and secret.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "LiveKit is not configured.");
        }

        if (authHeader == null || authHeader.isBlank()) {
            log.warn("LiveKit webhook rejected: Missing Authorization header.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization header.");
        }

        if (rawBody == null || rawBody.isBlank()) {
            log.warn("LiveKit webhook rejected: Empty request body.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty request body.");
        }

        try {
            WebhookReceiver receiver = new WebhookReceiver(properties.getApiKey(), properties.getApiSecret());
            WebhookEvent event = receiver.receive(rawBody, authHeader);
            log.debug("LiveKit webhook signature successfully verified for event: {}", event.getId());
            return event;
        } catch (Exception e) {
            log.warn("LiveKit webhook cryptographic verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature.");
        }
    }

    @Override
    public void processWebhookEvent(WebhookEvent event) {
        if (event == null) {
            return;
        }

        String eventId = event.getId();

        // 1. Deduplicate events defensively by event ID (prevents re-applying side effects on retries)
        if (eventId != null && !eventId.isBlank() && stringRedisTemplate != null) {
            String dedupKey = DEDUP_KEY_PREFIX + eventId;
            Boolean isNew = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "PROCESSED", DEDUP_TTL);
            if (Boolean.FALSE.equals(isNew)) {
                log.info("Duplicate LiveKit webhook event ignored: {}", eventId);
                return;
            }
        }

        String eventType = event.getEvent();
        String roomName = event.hasRoom() ? event.getRoom().getName() : "unknown";
        String participantIdentity = event.hasParticipant() ? event.getParticipant().getIdentity() : "unknown";

        log.info("Processing LiveKit webhook event: [type={}, id={}, room={}, participant={}]",
                eventType, eventId, roomName, participantIdentity);

        // Security rule: Webhook events do NOT override or grant PulseStream membership authorization.
        // PulseStream REST admission & STOMP session remain authoritative.
        switch (eventType) {
            case "participant_joined":
                log.info("LiveKit media plane: participant {} connected to room {}", participantIdentity, roomName);
                break;
            case "participant_left":
                log.info("LiveKit media plane: participant {} disconnected from room {}", participantIdentity, roomName);
                break;
            case "room_finished":
                log.info("LiveKit media plane: room {} finished", roomName);
                break;
            default:
                log.debug("Unhandled LiveKit webhook event type: {}", eventType);
                break;
        }
    }
}

