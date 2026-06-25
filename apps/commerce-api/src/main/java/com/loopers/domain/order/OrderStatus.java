package com.loopers.domain.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    CREATED("주문 생성됨"),
    PAID("결제 완료"),
    PAYMENT_FAILED("결제 실패");

    private final String description;
}
