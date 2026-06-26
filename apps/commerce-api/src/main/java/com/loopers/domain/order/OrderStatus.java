package com.loopers.domain.order;

public enum OrderStatus {
    PENDING,    // 주문 생성·재고 차감 완료, 결제 미확정 (초기 상태)
    PAID,       // 결제 성공 확정 (콜백/폴링으로 반영)
    FAILED      // 결제 실패 확정
}
