package com.loopers.tddstudy.messaging;

public record CatalogEvent(
        String eventId,      // UUID — 멱등 처리 키
        String eventType,    // PRODUCT_LIKED / PRODUCT_UNLIKED / ORDER_SALES
        Long productId,
        long amount,         // 좋아요 ±1, 판매 수량
        long occurredAt      // epoch millis (LocalDateTime 직렬화 이슈 회피)
) {
    public static final String TOPIC = "catalog-events";
}
