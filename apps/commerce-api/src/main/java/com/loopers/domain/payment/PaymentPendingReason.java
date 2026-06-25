package com.loopers.domain.payment;

public enum PaymentPendingReason {
    WAITING_CALLBACK,
    PG_REQUEST_FAILED,
    PG_LOOKUP_FAILED,
    PG_LOOKUP_EMPTY,
    CB_OPEN,
    TIMEOUT_UNKNOWN
}
