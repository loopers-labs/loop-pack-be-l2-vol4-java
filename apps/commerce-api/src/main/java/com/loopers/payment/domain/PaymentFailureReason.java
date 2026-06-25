package com.loopers.payment.domain;

public enum PaymentFailureReason {
    LIMIT_EXCEEDED,
    INVALID_CARD,
    PG_TRANSACTION_FAILED,
    PG_REQUEST_FAILED,
    PG_TIMEOUT,
    PG_UNAVAILABLE,
    PG_TRANSACTION_NOT_FOUND,
    UNKNOWN;

    public boolean isRequestFailure() {
        return this == PG_REQUEST_FAILED || this == PG_UNAVAILABLE || this == PG_TRANSACTION_NOT_FOUND;
    }

    public boolean isUnknownFailure() {
        return this == PG_TIMEOUT || this == UNKNOWN;
    }

    public boolean isTransactionFailure() {
        return this == LIMIT_EXCEEDED || this == INVALID_CARD || this == PG_TRANSACTION_FAILED;
    }
}
