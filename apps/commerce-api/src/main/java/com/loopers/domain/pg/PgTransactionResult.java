package com.loopers.domain.pg;

public record PgTransactionResult(String transactionKey, String status, String reason) {}
