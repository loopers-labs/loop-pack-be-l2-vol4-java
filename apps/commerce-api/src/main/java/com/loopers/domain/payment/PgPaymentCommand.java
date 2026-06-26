package com.loopers.domain.payment;

/**
 * PG 결제 요청에 필요한 정보. userId 는 PG 의 X-USER-ID 헤더로 전달된다.
 */
public record PgPaymentCommand(
    String userId,
    String orderId,
    CardType cardType,
    String cardNo,
    Long amount,
    String callbackUrl
) {
}
