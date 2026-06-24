package com.loopers.payment.application;

import com.loopers.payment.domain.CardType;

public class PaymentCommand {

    public record Pay(String orderNumber, CardType cardType, String cardNo) {
    }
}
