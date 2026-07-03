package com.loopers.infrastructure.payment;

import java.util.List;

/**
 * pg-simulator 주문별 거래 목록 조회(GET ?orderId=) 응답.
 */
public record PgOrderResponse(
        String orderId,
        List<PgTransactionResponse> transactions
) {
}
