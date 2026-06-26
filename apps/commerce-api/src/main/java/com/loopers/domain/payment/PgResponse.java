package com.loopers.domain.payment;

/**
 * PG 응답 캡슐. 우리는 호출 결과로 transactionKey + 상태 + 사유만 필요로 한다.
 * (orderId·금액·카드정보는 호출 시 우리가 보낸 값이므로 응답에서 재수신할 필요 없음)
 */
public record PgResponse(
    String transactionKey,
    PgStatus status,
    String reason
) {
}
