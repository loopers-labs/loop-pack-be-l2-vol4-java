package com.loopers.domain.payment;

public record PgPaymentRequest(
    String orderId,
    CardType cardType,
    String cardNo,
    Long amount,
    String callbackUrl
) {}
