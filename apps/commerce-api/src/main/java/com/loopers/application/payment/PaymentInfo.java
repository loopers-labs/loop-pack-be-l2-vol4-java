package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

import java.time.ZonedDateTime;

public record PaymentInfo(
    Long id,
    Long orderId,
    Long userId,
    CardType cardType,
    String cardNo,
    int amount,
    PaymentStatus status,
    String pgTransactionId,
    String failureCode,
    ZonedDateTime createdAt
) {
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getCardType(),
            payment.getCardNo(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getPgTransactionId(),
            payment.getFailureCode(),
            payment.getCreatedAt()
        );
    }
}
