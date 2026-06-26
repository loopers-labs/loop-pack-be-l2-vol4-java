package com.loopers.domain.payment;

/**
 * PG 결제 '조회' 결과. GET /payments/{txKey} 또는 ?orderId= 의 결과.
 * 처리 완료 시 status = SUCCESS/FAILED, reason 에 사유가 담긴다.
 */
public record PgTransactionResult(
        String transactionKey,
        String orderId,
        PgStatus status,
        String reason,
        long amount
) {}
