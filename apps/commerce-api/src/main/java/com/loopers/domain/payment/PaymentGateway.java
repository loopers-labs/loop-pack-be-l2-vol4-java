package com.loopers.domain.payment;

public interface PaymentGateway {
    PaymentGatewayResult requestPayment(PaymentGatewayCommand command);
    PaymentGatewayResult findTransaction(String userId, String transactionKey);
}
