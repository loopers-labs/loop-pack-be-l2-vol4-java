package com.loopers.payment.infrastructure;

import java.util.List;

/**
 * pg-simulator 의 주문 단위 거래 조회 응답({@code GET /payments?orderId=}). 한 orderId 에 엮인 거래 목록을 담는다
 * — PG 는 orderId 멱등이 아니라 여러 건일 수 있다(우리 흐름상 활성 PENDING 은 주문당 1건이라 보통 0~1건).
 */
public record PgOrderTransactions(
        String orderId,
        List<PgTransactionResponse> transactions
) {
}
