package com.loopers.domain.event.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
