package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.PaymentGatewayTransaction;
import com.loopers.payment.domain.PgPaymentStatus;

record PgSimulatorPaymentResponse(
    String transactionKey,
    PgPaymentStatus status,
    String reason
) {

    PaymentGatewayTransaction toTransaction() {
        return new PaymentGatewayTransaction(transactionKey, status, reason);
    }
}
