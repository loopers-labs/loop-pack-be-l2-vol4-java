package com.loopers.payment.infrastructure;

public record PgTransactionResponse(
        String transactionKey,
        String status,
        String reason
) {
}
