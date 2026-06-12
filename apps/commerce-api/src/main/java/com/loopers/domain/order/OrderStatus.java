package com.loopers.domain.order;

public enum OrderStatus {
    /** 주문 생성 — 결제 대기 */
    PENDING,
    /** 결제 완료 */
    PAID,
    /** 결제 실패 (보상 트랜잭션으로 재고 복구됨) */
    FAILED,
    /** 사용자 취소 */
    CANCELLED;

    public boolean isTerminal() {
        return this == PAID || this == FAILED || this == CANCELLED;
    }
}
