package com.loopers.application.outbox;

public record CatalogEventPayload(
    String eventId, String type, Long productId, long likeCount, long version, String occurredAt) {}
