package com.loopers.domain.order;

public enum OrderStatus {
    PENDING,    // 주문 생성 직후 (결제 대기)
    CONFIRMED,  // 결제 완료 (후주차에서 사용)
    CANCELLED   // 취소됨
}
