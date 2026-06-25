package com.loopers.domain.payment;

public interface PaymentGateway {
    PaymentGatewayResult requestPayment(PaymentGatewayCommand command);
}
