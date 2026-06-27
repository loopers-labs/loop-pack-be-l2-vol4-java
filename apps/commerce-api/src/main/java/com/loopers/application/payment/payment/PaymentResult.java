package com.loopers.application.payment.payment;

import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentStatus;

public class PaymentResult {
    public record Request(
        Long orderId,
        PaymentStatus paymentStatus,
        String transactionKey,
        String message
    ) {
        public static Request from(Payment payment, String message) {
            return new Request(
                payment.getOrderId(),
                payment.getStatus(),
                payment.getTransactionKey(),
                message
            );
        }
    }
}
