package com.loopers.application.metrics;

public record CatalogEventMessage(
    String eventId, String type, Long productId, long likeCount, long version, String occurredAt) {}
