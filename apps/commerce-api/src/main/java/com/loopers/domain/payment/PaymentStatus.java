package com.loopers.domain.payment;

/** 결제 생명주기 상태. 외부 PG 거래 상태와 별개로, 우리 결제 레코드 자체의 상태를 표현한다. */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
