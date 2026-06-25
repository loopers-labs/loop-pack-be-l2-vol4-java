package com.loopers.payment.domain;

public interface PaymentGateway {

    PaymentGatewayResult requestPayment(PaymentGatewayPaymentCommand command);
}
