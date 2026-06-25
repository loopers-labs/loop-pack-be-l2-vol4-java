package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentCardType;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentGatewayStatus;

public record PaymentCallbackCommand(
    String transactionKey,
    Long orderId,
    PaymentCardType cardType,
    String cardNo,
    Long amount,
    PaymentGatewayStatus status,
    String reason
) {
    public PaymentGatewayResult toGatewayResult() {
        return new PaymentGatewayResult(transactionKey, status, null, reason);
    }
}
