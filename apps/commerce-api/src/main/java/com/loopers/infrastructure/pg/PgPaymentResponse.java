package com.loopers.infrastructure.pg;

public record PgPaymentResponse(
    String transactionKey,
    String status,
    String reason
) {}
