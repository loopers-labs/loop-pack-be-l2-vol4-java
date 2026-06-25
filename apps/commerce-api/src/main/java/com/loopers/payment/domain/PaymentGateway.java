package com.loopers.payment.domain;

public interface PaymentGateway {

    PaymentGatewayResult requestPayment(PaymentGatewayPaymentCommand command);

    PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> getTransaction(Long userId, String transactionKey);

    PaymentGatewayQueryResult<PaymentGatewayOrderTransactions> getTransactionsByOrderId(Long userId, Long orderId);
}
