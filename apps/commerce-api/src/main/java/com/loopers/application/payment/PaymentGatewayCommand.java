package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentCardType;

public record PaymentGatewayCommand(
    String userLoginId,
    Long orderId,
    PaymentCardType cardType,
    String cardNo,
    Long amount
) {
}
