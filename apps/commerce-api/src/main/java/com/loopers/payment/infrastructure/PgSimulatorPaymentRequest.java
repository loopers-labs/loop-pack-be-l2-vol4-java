package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentGatewayPaymentCommand;

record PgSimulatorPaymentRequest(
    String orderId,
    CardType cardType,
    String cardNo,
    long amount,
    String callbackUrl
) {

    static PgSimulatorPaymentRequest from(PaymentGatewayPaymentCommand command, String callbackUrl) {
        return new PgSimulatorPaymentRequest(
            String.valueOf(command.orderId()),
            command.cardType(),
            command.cardNo(),
            command.amount(),
            callbackUrl
        );
    }
}
