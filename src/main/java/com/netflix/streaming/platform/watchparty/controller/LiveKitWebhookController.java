package com.netflix.streaming.platform.watchparty.controller;

import com.netflix.streaming.platform.watchparty.application.LiveKitWebhookService;
import livekit.LivekitWebhook.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller receiving authenticated webhook notifications from LiveKit Cloud / Server.
 */
@RestController
@RequestMapping("/api/webhooks/livekit")
public class LiveKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    private final LiveKitWebhookService webhookService;

    public LiveKitWebhookController(LiveKitWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Receives LiveKit webhook events.
     * Content-Type sent by LiveKit: application/webhook+json or application/json.
     *
     * <p>CRITICAL: Raw request body is preserved intact for cryptographic signature validation.
     */
    @PostMapping(consumes = { "application/webhook+json", MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE })
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody String rawBody) {

        log.debug("Received LiveKit webhook callback");

        WebhookEvent event = webhookService.verifyAndParse(rawBody, authHeader);
        webhookService.processWebhookEvent(event);

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}

