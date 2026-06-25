package com.loopers.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    PENDING("접수 대기"),
    SUCCESS("결제 성공"),
    FAILED("결제 실패");

    private final String description;
}
