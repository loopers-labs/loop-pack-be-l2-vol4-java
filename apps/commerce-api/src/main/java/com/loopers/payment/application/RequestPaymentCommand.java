package com.loopers.payment.application;

import com.loopers.payment.domain.CardType;

public record RequestPaymentCommand(
    Long userId,
    Long orderId,
    CardType cardType,
    String cardNo
) {
}
