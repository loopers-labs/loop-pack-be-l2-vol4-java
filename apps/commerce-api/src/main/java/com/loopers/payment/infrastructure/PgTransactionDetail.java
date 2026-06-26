package com.loopers.payment.infrastructure;

/**
 * pg-simulator 의 단건 거래 조회 응답({@code GET /payments/{transactionKey}}). cardType·cardNo 등은 수신만 하고 사용하지 않는다.
 */
public record PgTransactionDetail(
        String transactionKey,
        String orderId,
        long amount,
        String status,
        String reason
) {
}
