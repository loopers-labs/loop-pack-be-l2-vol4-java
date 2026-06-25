package com.loopers.domain.payment;

public interface PaymentGateway {

    String requestPayment(PaymentModel payment);
}
