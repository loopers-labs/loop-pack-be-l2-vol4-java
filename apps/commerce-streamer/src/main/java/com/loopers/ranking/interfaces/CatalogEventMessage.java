package com.loopers.ranking.interfaces;

import java.time.ZonedDateTime;

/**
 * catalog-events 에서 랭킹이 소비하는 필드. producer(commerce-api) JSON 중 필요한 것만 담는다
 * (eventId 등 나머지는 무시 — FAIL_ON_UNKNOWN_PROPERTIES 비활성). occurredAt 으로 날짜를 버킷팅한다.
 */
public record CatalogEventMessage(
        Long productId,
        CatalogEventType type,
        long delta,
        ZonedDateTime occurredAt
) {
}
