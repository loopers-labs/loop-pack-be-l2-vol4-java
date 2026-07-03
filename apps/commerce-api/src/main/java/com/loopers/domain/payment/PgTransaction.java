package com.loopers.domain.payment;

/**
 * PG 거래 조회/응답 결과(domain). PG 의 TransactionStatus 는 어댑터에서 우리 {@link PaymentStatus} 로 매핑해 담는다
 * (SUCCESS→PAID, FAILED→FAILED, PENDING→PENDING).
 * {@code amount} 는 무결성 가드용 — PG 응답이 제공할 때만 채워지고(상세 조회), 없으면 null(접수 응답·주문별 목록).
 */
public record PgTransaction(
        String transactionKey,
        PaymentStatus status,
        String reason,
        Long amount
) {
}
