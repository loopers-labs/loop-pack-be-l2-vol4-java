package com.loopers.metrics.interfaces;

/**
 * catalog-events 토픽에서 소비하는 상품 집계 이벤트.
 * producer(commerce-api)의 CatalogEventMessage 와 필드명이 일치해야 한다.
 */
public record CatalogEventMessage(
        String eventId,
        Long productId,
        CatalogEventType type,
        long delta
) {
}
