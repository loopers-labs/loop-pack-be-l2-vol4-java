package com.loopers.infrastructure.pg;

public class PgRequest {

    public record CreateTransaction(
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String callbackUrl
    ) {}
}
