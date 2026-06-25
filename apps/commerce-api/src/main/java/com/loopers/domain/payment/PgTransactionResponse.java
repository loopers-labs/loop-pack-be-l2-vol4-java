package com.loopers.domain.payment;

public record PgTransactionResponse(
    String transactionKey,
    PgTransactionStatus status,
    String reason
) {}
