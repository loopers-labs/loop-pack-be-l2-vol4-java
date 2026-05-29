package com.loopers.domain.order;

public enum OrderStatus {
    PENDING,     // 주문 생성됨 (결제 대기) — Round 3 는 여기까지
    COMPLETED,   // 결제 성공, 주문 확정 (다음 라운드)
    FAILED       // 결제/검증 실패 (다음 라운드)
}
