package com.loopers.domain.payment;

/**
 * 내부 결제 상태. PG 의 {@link PgTransactionStatus} 와 1:1 로 대응한다.
 * - PENDING: 요청 접수, 최종 결과 대기(콜백/폴링으로 확정)
 * - SUCCESS: 결제 승인
 * - FAILED: 한도 초과·잘못된 카드 등으로 최종 실패
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
