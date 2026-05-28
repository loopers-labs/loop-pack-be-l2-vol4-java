package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

import java.time.ZonedDateTime;
import java.util.UUID;

public record PaymentInfo(
    UUID id,
    UUID orderId,
    String pgTransactionId,
    PaymentStatus status,
    Long amount,
    ZonedDateTime createdAt
) {

    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.getOrderId(),
            payment.getPgTransactionId(),
            payment.getStatus(),
            payment.getAmount(),
            payment.getCreatedAt()
        );
    }
}
