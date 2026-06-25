package com.loopers.application.payment;

public class PaymentCommand {

    public record Pay(Long orderId, String cardType, String cardNo) {
    }
}
