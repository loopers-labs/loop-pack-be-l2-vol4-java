package com.loopers.domain.payment;

import java.math.BigDecimal;

public record PaymentGatewayRequest(
        String userNumber,
        String orderNumber,
        CardType cardType,
        String cardNo,
        BigDecimal amount
) {
}
