package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.PaymentMethod;

import java.math.BigDecimal;

public class PaymentV1Dto {

    public record PaymentRequest(
        Long orderId,
        PaymentMethod paymentMethod,
        BigDecimal amount
    ) {}

    public record PaymentResponse(
        Long paymentId
    ) {}
}
