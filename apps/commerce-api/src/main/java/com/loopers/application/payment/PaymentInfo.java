package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
        Long orderId,
        PaymentStatus status,
        String transactionKey,
        Long amount,
        String reason
) {
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
                payment.getOrderId(),
                payment.getStatus(),
                payment.getTransactionKey(),
                payment.getAmount(),
                payment.getReason()
        );
    }
}