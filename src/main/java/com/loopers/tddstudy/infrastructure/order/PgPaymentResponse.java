package com.loopers.tddstudy.infrastructure.order;

public record PgPaymentResponse(
        String transactionKey,
        String status,
        String reason
) {}
