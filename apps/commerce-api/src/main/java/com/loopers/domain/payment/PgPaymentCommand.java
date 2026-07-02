package com.loopers.domain.payment;

public record PgPaymentCommand(
        Long orderId,
        Long userId,
        Long amount,
        CardType cardType,
        String cardNo
) {
    public static PgPaymentCommand of(Long orderId, Long userId, Long amount, CardType cardType, String cardNo) {
        return new PgPaymentCommand(orderId, userId, amount, cardType, cardNo);
    }
}