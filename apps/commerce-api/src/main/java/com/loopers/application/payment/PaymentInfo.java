package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
    Long paymentId,
    Long orderId,
    Long userId,
    Long amount,
    CardType cardType,
    PaymentStatus status,
    String transactionKey,
    String failReason
) {
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCardType(),
            payment.getStatus(),
            payment.getTransactionKey(),
            payment.getFailReason()
        );
    }
}
