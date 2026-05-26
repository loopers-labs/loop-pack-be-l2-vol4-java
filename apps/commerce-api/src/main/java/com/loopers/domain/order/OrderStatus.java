package com.loopers.domain.order;

/**
 * 주문 상태. 결제 연동은 후주차에서 다루며, 이번 라운드에서는 주문 생성 시 CREATED 로 시작한다.
 */
public enum OrderStatus {
    CREATED,
    PAID,
    FAILED
}
