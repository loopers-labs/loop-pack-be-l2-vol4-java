package com.loopers.order.domain;

/**
 * 주문 상태. PENDING 으로 생성되며, PAID/FAILED 는 결제 도메인이 추가될 때 사용한다.
 * 현재 범위에서는 주문이 생성 직후 PENDING 으로 종료된다.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    FAILED
}
