package com.loopers.infrastructure.pg;

public record PgTransactionResponse(
    String transactionKey,
    String status,   // PENDING | SUCCESS | FAILED
    String reason
) {}
