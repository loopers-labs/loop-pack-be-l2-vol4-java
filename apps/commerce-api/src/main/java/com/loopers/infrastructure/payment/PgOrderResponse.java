package com.loopers.infrastructure.payment;

import java.util.List;

/** PG 주문 기준 조회(GET ?orderId=) 응답의 data 부분. */
public record PgOrderResponse(
    String orderId,
    List<PgTransactionResponse> transactions
) {
}
