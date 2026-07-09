package com.loopers.tddstudy.infrastructure.order;

public record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String callbackUrl
) {}
