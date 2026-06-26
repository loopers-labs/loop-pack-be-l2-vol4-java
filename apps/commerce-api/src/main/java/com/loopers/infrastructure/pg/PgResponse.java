package com.loopers.infrastructure.pg;

import java.util.List;

public class PgResponse {

    public record TransactionResponse(
        String transactionKey,
        String status,
        String reason
    ) {}

    public record OrderResponse(
        String orderId,
        List<TransactionResponse> transactions
    ) {}
}
