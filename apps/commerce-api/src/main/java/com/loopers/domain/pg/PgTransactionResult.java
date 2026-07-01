package com.loopers.domain.pg;

public record PgTransactionResult(String transactionKey, PgTransactionStatus status, String reason) {}
