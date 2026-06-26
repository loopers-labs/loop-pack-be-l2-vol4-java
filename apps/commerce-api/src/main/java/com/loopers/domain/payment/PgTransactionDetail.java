package com.loopers.domain.payment;

/**
 * PG 상태 확인 API 로 조회한 결제 상세. 콜백 유실/타임아웃 시 복구의 기준이 된다.
 */
public record PgTransactionDetail(
    String transactionKey,
    String orderId,
    CardType cardType,
    String cardNo,
    Long amount,
    PgTransactionStatus status,
    String reason
) {
}
