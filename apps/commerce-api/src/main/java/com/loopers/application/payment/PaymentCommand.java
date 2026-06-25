package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

public record PaymentCommand(Long orderId, CardType cardType, String cardNo) {

    public static PaymentCommand of(Long orderId, CardType cardType, String cardNo) {
        return new PaymentCommand(orderId, cardType, cardNo);
    }
}
