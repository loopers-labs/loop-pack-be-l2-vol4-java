package com.loopers.infrastructure.payment;

/**
 * pg-simulator 접수 응답(POST) 및 주문별 목록의 거래 요약. amount·cardNo 는 포함되지 않는다.
 */
public record PgTransactionResponse(
        String transactionKey,
        String status,
        String reason
) {
}
