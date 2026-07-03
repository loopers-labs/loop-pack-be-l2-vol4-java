package com.loopers.domain.like;

import com.loopers.domain.outbox.OutboxableEvent;

// T2 시점(product_stats 카운트 실제 반영 후) fact. T1(UnlikedEvent)과 별개로 시스템 간 전파(Kafka)만 담당한다.
public record ProductUnlikedEvent(String eventId, Long productId) implements OutboxableEvent {

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
        return "PRODUCT_UNLIKED";
    }
}
