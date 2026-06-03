package com.loopers.infrastructure.event.outbox;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.event.outbox.OrderEventOutbox;
import com.loopers.domain.event.outbox.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_event_outbox")
public class OrderEventOutboxJpaEntity extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    protected OrderEventOutboxJpaEntity() {}

    private OrderEventOutboxJpaEntity(
        Long orderId,
        String eventType,
        String payload,
        OutboxStatus status,
        Integer retryCount
    ) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.retryCount = retryCount;
    }

    public static OrderEventOutboxJpaEntity from(OrderEventOutbox outbox) {
        return new OrderEventOutboxJpaEntity(
            outbox.getOrderId(),
            outbox.getEventType(),
            outbox.getPayload(),
            outbox.getStatus(),
            outbox.getRetryCount()
        );
    }

    public OrderEventOutbox toDomain() {
        return OrderEventOutbox.reconstruct(
            getId(),
            orderId,
            eventType,
            payload,
            status,
            retryCount,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(OrderEventOutbox outbox) {
        this.orderId = outbox.getOrderId();
        this.eventType = outbox.getEventType();
        this.payload = outbox.getPayload();
        this.status = outbox.getStatus();
        this.retryCount = outbox.getRetryCount();
    }
}
