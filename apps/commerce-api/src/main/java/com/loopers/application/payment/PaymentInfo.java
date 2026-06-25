package com.loopers.application.payment;

import com.loopers.domain.payment.model.PaymentStatus;

public record PaymentInfo(
    Long paymentId,
    PaymentStatus status
) {
}
