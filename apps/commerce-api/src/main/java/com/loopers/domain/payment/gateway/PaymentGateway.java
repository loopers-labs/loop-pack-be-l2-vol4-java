package com.loopers.domain.payment.gateway;

public interface PaymentGateway {
    PaymentGatewayResult authorize(Long orderId, Long amount, String idempotencyKey);

    PaymentGatewayResult capture(String transactionKey);

    PaymentGatewayResult voidAuthorization(String transactionKey);
}
