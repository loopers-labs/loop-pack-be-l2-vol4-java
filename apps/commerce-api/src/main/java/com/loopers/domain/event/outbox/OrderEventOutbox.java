package com.loopers.domain.event.outbox;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class OrderEventOutbox extends DomainEntity {

    public static final String ORDER_PAID = "ORDER_PAID";

    private Long orderId;

    private String eventType;

    private String payload;

    private OutboxStatus status;

    private Integer retryCount;

    public OrderEventOutbox(Long orderId, String eventType, String payload) {
        validateOrderId(orderId);
        validateEventType(eventType);
        validatePayload(payload);

        this.orderId = orderId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public static OrderEventOutbox reconstruct(
        Long id,
        Long orderId,
        String eventType,
        String payload,
        OutboxStatus status,
        Integer retryCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        OrderEventOutbox outbox = new OrderEventOutbox(orderId, eventType, payload);
        outbox.status = status == null ? outbox.status : status;
        outbox.retryCount = retryCount == null ? outbox.retryCount : retryCount;
        outbox.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return outbox;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public boolean isPending() {
        return status == OutboxStatus.PENDING;
    }

    public void markSent() {
        if (!isPending()) {
            return;
        }

        this.status = OutboxStatus.SENT;
    }

    public void recordFailure(int maxRetryCount) {
        if (!isPending()) {
            return;
        }
        if (maxRetryCount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최대 재시도 횟수는 1 이상이어야 합니다.");
        }

        this.retryCount++;
        if (retryCount >= maxRetryCount) {
            this.status = OutboxStatus.FAILED;
        }
    }

    private void validateOrderId(Long value) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
    }

    private void validateEventType(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트 타입은 필수입니다.");
        }
    }

    private void validatePayload(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트 payload는 필수입니다.");
        }
    }
}
