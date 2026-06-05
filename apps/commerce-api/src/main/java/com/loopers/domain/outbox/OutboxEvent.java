package com.loopers.domain.outbox;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class OutboxEvent {

    private Long id;
    private String eventType;
    private String payload;
    private OutboxStatus status;
    private int retryCount;
    private ZonedDateTime createdAt;
    private ZonedDateTime processedAt;

    public OutboxEvent(String eventType, String payload) {
        validate(eventType, payload);
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public OutboxEvent(Long id, String eventType, String payload, OutboxStatus status,
                       int retryCount, ZonedDateTime createdAt, ZonedDateTime processedAt) {
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public void markProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = ZonedDateTime.now();
    }

    private void validate(String eventType, String payload) {
        if (eventType == null || eventType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트 타입은 필수입니다.");
        }
        if (payload == null || payload.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이로드는 필수입니다.");
        }
    }
}
