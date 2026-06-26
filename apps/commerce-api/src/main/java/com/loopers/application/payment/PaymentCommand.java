package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

/**
 * 결제 요청 커맨드.
 *  - cardNo 는 원본. Facade 가 PG 호출에 잠깐 사용한 뒤 마지막 4자리만 영속화.
 */
public record PaymentCommand(
    Long orderId,
    CardType cardType,
    String cardNo
) {
}
