package com.loopers.domain.payment;

import com.loopers.domain.payment.model.CardType;

public record PaymentGatewayCommand(
    String userId,
    String orderId,
    CardType cardType,
    String cardNo,
    Long amount,
    String callbackUrl
) {
}
