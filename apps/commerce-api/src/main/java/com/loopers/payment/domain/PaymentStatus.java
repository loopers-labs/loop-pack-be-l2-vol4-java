package com.loopers.payment.domain;

public enum PaymentStatus {
    REQUESTING,
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
    REQUEST_FAILED;

    public boolean isActive() {
        return this == REQUESTING || this == PENDING || this == UNKNOWN;
    }
}
