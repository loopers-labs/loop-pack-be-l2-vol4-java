package com.loopers.domain.eventpublish;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED;

    public boolean isSent() {
        return this == SENT;
    }
}
