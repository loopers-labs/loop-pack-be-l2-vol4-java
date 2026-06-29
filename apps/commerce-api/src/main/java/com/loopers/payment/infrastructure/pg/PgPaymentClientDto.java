package com.loopers.payment.infrastructure.pg;

import java.util.List;

public class PgPaymentClientDto {

    public record PaymentRequest(
        String idempotencyKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}

    public record TransactionResponse(
        String transactionKey,
        String status,
        String reason
    ) {}

    public record OrderTransactionsResponse(
        String orderId,
        List<TransactionResponse> transactions
    ) {}
}
