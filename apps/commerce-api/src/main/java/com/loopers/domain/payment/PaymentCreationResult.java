package com.loopers.domain.payment;

public record PaymentCreationResult(Payment payment, boolean created) {

    public static PaymentCreationResult created(Payment payment) {
        return new PaymentCreationResult(payment, true);
    }

    public static PaymentCreationResult existing(Payment payment) {
        return new PaymentCreationResult(payment, false);
    }
}
