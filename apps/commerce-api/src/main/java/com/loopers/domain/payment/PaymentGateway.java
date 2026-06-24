package com.loopers.domain.payment;

public interface PaymentGateway {

    PaymentGatewayResponse requestPayment(PaymentGatewayRequest request);
}
