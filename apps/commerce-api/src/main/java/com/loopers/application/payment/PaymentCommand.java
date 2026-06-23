package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

public class PaymentCommand {

    public record Request(
        Long userId,
        Long orderId,
        CardType cardType,
        String cardNo
    ) {}
}
