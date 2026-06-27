package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
    Long paymentId,
    Long orderId,
    int amount,
    PaymentStatus status,
    String transactionKey
) {

    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.getOrderId(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getTransactionKey()
        );
    }
}
