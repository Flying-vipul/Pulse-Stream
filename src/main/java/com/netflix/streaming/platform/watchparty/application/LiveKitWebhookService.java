package com.netflix.streaming.platform.watchparty.application;

import livekit.LivekitWebhook.WebhookEvent;

/**
 * Service contract for verifying and safely processing incoming LiveKit webhooks.
 */
public interface LiveKitWebhookService {

    /**
     * Cryptographically validates the LiveKit webhook Authorization header against the raw body.
     *
     * @param rawBody Unmodified HTTP request body string
     * @param authHeader Authorization header containing LiveKit JWT signature
     * @return Validated WebhookEvent
     */
    WebhookEvent verifyAndParse(String rawBody, String authHeader);

    /**
     * Processes verified LiveKit webhook events with idempotency and deduplication.
     *
     * @param event Validated WebhookEvent
     */
    void processWebhookEvent(WebhookEvent event);
}

