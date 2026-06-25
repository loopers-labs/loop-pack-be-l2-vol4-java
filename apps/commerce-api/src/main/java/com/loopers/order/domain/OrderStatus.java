package com.loopers.order.domain;

/**
 * 주문 상태. 결제 흐름과 맞물린다.
 * PENDING_PAYMENT(생성·재고 선점, 결제 대기) → 콜백 SUCCESS 시 PAID / FAILED 시 PAYMENT_FAILED(+보상).
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED
}
