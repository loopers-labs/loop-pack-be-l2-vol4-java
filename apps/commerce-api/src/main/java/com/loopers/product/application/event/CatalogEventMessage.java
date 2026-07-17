package com.loopers.product.application.event;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * catalog-events 토픽으로 발행되는 상품 집계 이벤트.
 * - eventId    : 멱등 key(consumer 의 event_handled). delta 합산은 중복 적용되면 안 되므로 필수.
 * - delta      : LIKE 는 +1(등록)/-1(취소), VIEW 는 +1.
 * - occurredAt : 이벤트 발생 시각. 랭킹이 일별 키를 이 시각(KST)으로 버킷팅한다(처리시각 아님).
 */
public record CatalogEventMessage(
        String eventId,
        Long productId,
        CatalogEventType type,
        long delta,
        ZonedDateTime occurredAt
) {
    public static CatalogEventMessage like(Long productId, long delta) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), productId, CatalogEventType.LIKE, delta, ZonedDateTime.now());
    }

    public static CatalogEventMessage view(Long productId) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), productId, CatalogEventType.VIEW, 1, ZonedDateTime.now());
    }
}
