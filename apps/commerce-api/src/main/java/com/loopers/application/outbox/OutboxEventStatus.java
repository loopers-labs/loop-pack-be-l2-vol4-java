package com.loopers.application.outbox;

public enum OutboxEventStatus {
    READY,
    PUBLISHED,
    FAILED
}
