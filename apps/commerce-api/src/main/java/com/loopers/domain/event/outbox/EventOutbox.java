package com.loopers.domain.event.outbox;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;
import java.util.UUID;

public class EventOutbox extends DomainEntity {

    public static final String TOPIC_CATALOG_EVENTS = "catalog-events";
    public static final String TOPIC_ORDER_EVENTS = "order-events";
    public static final String TOPIC_COUPON_ISSUE_REQUESTS = "coupon-issue-requests";
    public static final String EVENT_ORDER_PAID = "ORDER_PAID";
    public static final String EVENT_PRODUCT_LIKED = "PRODUCT_LIKED";
    public static final String EVENT_PRODUCT_UNLIKED = "PRODUCT_UNLIKED";
    public static final String EVENT_COUPON_ISSUE_REQUESTED = "COUPON_ISSUE_REQUESTED";
    public static final String AGGREGATE_ORDER = "ORDER";
    public static final String AGGREGATE_PRODUCT = "PRODUCT";
    public static final String AGGREGATE_COUPON_TEMPLATE = "COUPON_TEMPLATE";

    private String eventId;
    private String topic;
    private String partitionKey;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private String payload;
    private OutboxStatus status;
    private Integer retryCount;

    public EventOutbox(
        String topic,
        String partitionKey,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload
    ) {
        this(
            UUID.randomUUID().toString(),
            topic,
            partitionKey,
            eventType,
            aggregateType,
            aggregateId,
            payload,
            OutboxStatus.PENDING,
            0
        );
    }

    private EventOutbox(
        String eventId,
        String topic,
        String partitionKey,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        OutboxStatus status,
        Integer retryCount
    ) {
        validateRequired(eventId, "이벤트 ID는 필수입니다.");
        validateRequired(topic, "이벤트 topic은 필수입니다.");
        validateRequired(partitionKey, "이벤트 partition key는 필수입니다.");
        validateRequired(eventType, "이벤트 타입은 필수입니다.");
        validateRequired(aggregateType, "이벤트 aggregate type은 필수입니다.");
        validateRequired(aggregateId, "이벤트 aggregate ID는 필수입니다.");
        validateRequired(payload, "이벤트 payload는 필수입니다.");

        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = status == null ? OutboxStatus.PENDING : status;
        this.retryCount = retryCount == null ? 0 : retryCount;
    }

    public static EventOutbox reconstruct(
        Long id,
        String eventId,
        String topic,
        String partitionKey,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        OutboxStatus status,
        Integer retryCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        EventOutbox outbox = new EventOutbox(
            eventId,
            topic,
            partitionKey,
            eventType,
            aggregateType,
            aggregateId,
            payload,
            status,
            retryCount
        );
        outbox.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return outbox;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
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

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }
}
