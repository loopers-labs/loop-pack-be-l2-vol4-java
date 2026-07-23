package com.loopers.metrics.interfaces;

import java.time.ZonedDateTime;

/**
 * catalog-events 토픽에서 소비하는 상품 집계 이벤트.
 * producer(commerce-api)의 CatalogEventMessage 와 필드명이 일치해야 한다.
 * occurredAt 은 producer 가 예전부터 실어 보내던 값이다 — 선언하지 않는 동안 Jackson 이 버리고 있었다.
 */
public record CatalogEventMessage(
        String eventId,
        Long productId,
        CatalogEventType type,
        long delta,
        ZonedDateTime occurredAt
) {
}
