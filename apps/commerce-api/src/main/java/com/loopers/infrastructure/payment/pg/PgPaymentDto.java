package com.loopers.infrastructure.payment.pg;

public class PgPaymentDto {

    public record PaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String callbackUrl
    ) {}

    public record TransactionResponse(
        String transactionKey,
        String status,
        String reason
    ) {}

    public record OrderTransactionResponse(
        String orderId,
        java.util.List<TransactionResponse> transactions
    ) {}
}
