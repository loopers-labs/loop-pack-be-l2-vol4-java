package com.loopers.payment.application;

import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PgPaymentStatus;

public record PaymentCallbackCommand(
    String transactionKey,
    Long orderId,
    long amount,
    CardType cardType,
    PgPaymentStatus status,
    PaymentFailureReason failureReason,
    String reason
) {

    public boolean isPending() {
        return status == PgPaymentStatus.PENDING;
    }

    public boolean isSucceeded() {
        return status == PgPaymentStatus.SUCCESS;
    }
}
