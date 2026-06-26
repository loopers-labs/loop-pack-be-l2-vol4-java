package com.loopers.domain.payment;

/**
 * PG 결제 요청에 필요한 도메인 입력.
 * callbackUrl·X-USER-ID 같은 인프라 설정값은 Gateway 구현체가 채우므로 여기 없다.
 */
public record PgPaymentCommand(
        Long orderId,
        CardType cardType,
        String cardNo,
        long amount
) {}
