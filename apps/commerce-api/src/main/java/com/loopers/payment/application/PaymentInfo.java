package com.loopers.payment.application;

import com.loopers.payment.domain.PaymentModel;
import com.loopers.payment.domain.PaymentStatus;

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
