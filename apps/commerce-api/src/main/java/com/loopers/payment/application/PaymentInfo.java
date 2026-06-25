package com.loopers.payment.application;

import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgPaymentStatus;

import java.time.ZonedDateTime;

public record PaymentInfo(
    Long id,
    Long userId,
    Long orderId,
    long amount,
    CardType cardType,
    String maskedCardNo,
    PaymentStatus status,
    PaymentFailureReason failureReason,
    String pgTransactionKey,
    PgPaymentStatus pgStatus,
    String pgReason,
    ZonedDateTime requestedAt,
    ZonedDateTime completedAt
) {

    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.getUserId(),
            payment.getOrderId(),
            payment.getAmount(),
            payment.getCardType(),
            payment.getMaskedCardNo(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getPgTransactionKey(),
            payment.getPgStatus(),
            payment.getPgReason(),
            payment.getRequestedAt(),
            payment.getCompletedAt()
        );
    }
}
