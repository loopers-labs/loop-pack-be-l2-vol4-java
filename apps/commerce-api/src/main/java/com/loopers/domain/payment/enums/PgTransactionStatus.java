package com.loopers.domain.payment.enums;

public enum PgTransactionStatus {
    SUCCESS,
    FAILED,
    PENDING;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
