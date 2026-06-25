package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.PaymentGatewayOrderTransactions;

import java.util.List;

record PgSimulatorOrderPaymentResponse(
    String orderId,
    List<PgSimulatorPaymentResponse> transactions
) {

    PaymentGatewayOrderTransactions toOrderTransactions() {
        return new PaymentGatewayOrderTransactions(
            Long.valueOf(orderId),
            transactions.stream()
                .map(PgSimulatorPaymentResponse::toTransaction)
                .toList()
        );
    }
}
