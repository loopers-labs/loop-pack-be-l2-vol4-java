package com.loopers.domain.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PaymentGateway {
    PaymentGatewayResult requestPayment(Long orderId, BigDecimal amount, PaymentMethod method);
    void cancelPayment(String transactionId, BigDecimal amount);

    record PaymentGatewayResult(
        String transactionId,
        LocalDateTime approvedAt
    ) {}
}
