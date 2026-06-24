package com.loopers.payment.domain;

public interface PaymentGateway {

    PaymentGatewayResult request(PaymentGatewayCommand command);
}
