package com.loopers.domain.payment;

/**
 * PG 거래 상태 조회 결과. amount 는 주문별 목록 조회 시 PG 가 주지 않을 수 있어 nullable.
 */
public record PgTransaction(
        String transactionKey,
        PaymentStatus status,
        Long amount,
        String reason
) {
}