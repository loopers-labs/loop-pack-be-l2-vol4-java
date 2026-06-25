package com.loopers.payment.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentGatewayTransactionDetail;
import com.loopers.payment.domain.PgPaymentStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
record PgSimulatorPaymentDetailResponse(
    String transactionKey,
    String orderId,
    CardType cardType,
    long amount,
    PgPaymentStatus status,
    String reason
) {

    PaymentGatewayTransactionDetail toTransactionDetail() {
        return new PaymentGatewayTransactionDetail(
            transactionKey,
            Long.valueOf(orderId),
            cardType,
            amount,
            status,
            reason
        );
    }
}
