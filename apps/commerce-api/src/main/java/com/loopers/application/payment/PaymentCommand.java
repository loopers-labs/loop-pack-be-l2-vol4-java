package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

public record PaymentCommand(
    Long orderId,
    CardType cardType,
    String cardNo
) {
}
