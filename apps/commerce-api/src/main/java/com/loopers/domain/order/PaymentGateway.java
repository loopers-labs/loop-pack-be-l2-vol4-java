package com.loopers.domain.order;

public interface PaymentGateway {

    PaymentResult requestPayment(PaymentCommand command);
}