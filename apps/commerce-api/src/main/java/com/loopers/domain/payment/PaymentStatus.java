package com.loopers.domain.payment;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    /** 키가 끝내 안 붙은 미아 PENDING을 PG 대사 후에도 결제건이 없어 만료 종결한 상태. */
    EXPIRED
}
