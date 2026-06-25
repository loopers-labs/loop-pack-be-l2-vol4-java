package com.loopers.infrastructure.pg;

public record PgCallbackPayload(
    String transactionKey,
    String orderId,
    String status,   // SUCCESS | FAILED
    String reason
) {}
