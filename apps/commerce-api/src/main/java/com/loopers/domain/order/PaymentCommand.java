package com.loopers.domain.order;

public record PaymentCommand(Long orderId, Long amount) {

    public static PaymentCommand of(Long orderId, Long amount) {
        return new PaymentCommand(orderId, amount);
    }
}