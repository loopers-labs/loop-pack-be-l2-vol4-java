package com.loopers.payment.application;

import com.loopers.payment.domain.PaymentFailureReason;

public final class PgPaymentFailureReason {

    private PgPaymentFailureReason() {
    }

    public static PaymentFailureReason resolve(String reason) {
        if (reason == null) {
            return PaymentFailureReason.PG_TRANSACTION_FAILED;
        }
        if (reason.contains("한도")) {
            return PaymentFailureReason.LIMIT_EXCEEDED;
        }
        if (reason.contains("카드")) {
            return PaymentFailureReason.INVALID_CARD;
        }
        return PaymentFailureReason.PG_TRANSACTION_FAILED;
    }
}
