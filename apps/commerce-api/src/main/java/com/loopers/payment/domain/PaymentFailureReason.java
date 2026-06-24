package com.loopers.payment.domain;

public enum PaymentFailureReason {
    LIMIT_EXCEEDED,
    INVALID_CARD,
    PG_REQUEST_FAILED,
    PG_TIMEOUT,
    PG_UNAVAILABLE,
    PG_TRANSACTION_NOT_FOUND,
    UNKNOWN
}
