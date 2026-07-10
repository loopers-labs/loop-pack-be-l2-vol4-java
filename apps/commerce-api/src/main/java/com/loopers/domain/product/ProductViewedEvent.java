package com.loopers.domain.product;

import com.loopers.domain.UserActivityEvent;

import java.util.UUID;

// 비로그인으로도 호출 가능한 상세 조회(GET /products/{id})라 userId를 알 수 없다.
public record ProductViewedEvent(String eventId, Long productId) implements UserActivityEvent {

    public static ProductViewedEvent of(Long productId) {
        return new ProductViewedEvent(UUID.randomUUID().toString(), productId);
    }

    @Override
    public Long userId() {
        return null;
    }

    @Override
    public String aggregateType() {
        return "Product";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(productId);
    }

    @Override
    public String eventType() {
        return "PRODUCT_VIEWED";
    }
}
