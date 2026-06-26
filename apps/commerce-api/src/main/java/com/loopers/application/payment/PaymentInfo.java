package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
    Long paymentId,
    Long orderId,
    String transactionKey,
    PaymentStatus status,
    Long amount
) {
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.getOrderId(),
            payment.getTransactionKey(),
            payment.getStatus(),
            payment.getAmount()
        );
    }
}
