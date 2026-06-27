package com.loopers.infrastructure.payment.gateway;

import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayCommand;
import com.loopers.domain.payment.gateway.PaymentGatewayResult;

public class LocalPaymentGateway implements PaymentGateway {

    @Override
    public PaymentGatewayResult requestPayment(PaymentGatewayCommand.Request command) {
        return PaymentGatewayResult.pending("tx-" + command.orderId());
    }

    @Override
    public PaymentGatewayResult authorize(Long orderId, Long amount, String idempotencyKey) {
        return PaymentGatewayResult.success("tx-" + orderId);
    }

    @Override
    public PaymentGatewayResult capture(String transactionKey) {
        return PaymentGatewayResult.success(transactionKey);
    }

    @Override
    public PaymentGatewayResult voidAuthorization(String transactionKey) {
        return PaymentGatewayResult.success(transactionKey);
    }
}
