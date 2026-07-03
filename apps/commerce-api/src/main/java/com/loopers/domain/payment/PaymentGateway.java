package com.loopers.domain.payment;

import java.util.List;

public interface PaymentGateway {
    PaymentGatewayResult requestPayment(PaymentGatewayCommand command);
    PaymentGatewayResult findTransaction(String userId, String transactionKey);
    List<PaymentGatewayResult> findTransactionsByOrder(String userId, String orderId);
}
