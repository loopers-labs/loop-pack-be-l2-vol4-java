package com.loopers.domain.payment;

import java.math.BigDecimal;

public record PgPaymentCommand(
        Long userId,
        Long orderId,
        CardType cardType,
        String cardNo,
        BigDecimal amount
) {
}
