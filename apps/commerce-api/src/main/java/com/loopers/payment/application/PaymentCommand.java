package com.loopers.payment.application;

import com.loopers.payment.domain.CardType;

public class PaymentCommand {

    public record Pay(Long userId, String orderNumber, CardType cardType, String cardNo) {
    }
}
