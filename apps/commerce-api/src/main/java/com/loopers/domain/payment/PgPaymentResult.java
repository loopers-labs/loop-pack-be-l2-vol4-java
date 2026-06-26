package com.loopers.domain.payment;

/**
 * PG 결제 요청 접수 결과. 비동기 결제이므로 접수 직후 status 는 보통 PENDING 이다.
 */
public record PgPaymentResult(
    String transactionKey,
    PgTransactionStatus status,
    String reason
) {
}
