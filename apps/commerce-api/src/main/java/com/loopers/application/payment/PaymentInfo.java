package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentEntity;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
    String paymentId,
    String orderId,
    String transactionKey,
    CardType cardType,
    Long amount,
    PaymentStatus status,
    String failureReason
) {
    public static PaymentInfo from(PaymentEntity entity) {
        return new PaymentInfo(
            entity.getId(),
            entity.getOrderId(),
            entity.getTransactionKey(),
            entity.getCardType(),
            entity.getAmount(),
            entity.getStatus(),
            entity.getFailureReason()
        );
    }
}
