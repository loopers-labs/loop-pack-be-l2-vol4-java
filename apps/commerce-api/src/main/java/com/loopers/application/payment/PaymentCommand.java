package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

public class PaymentCommand {

    public record Request(
        Long userId,
        Long orderId,
        CardType cardType,
        String cardNo
    ) {}

    public record Callback(
        String transactionKey,
        PaymentStatus status,
        String reason
    ) {}
}
