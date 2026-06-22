package com.loopers.payment.infrastructure.pg;

public class PgPaymentClientDto {

    public record PaymentRequest(
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
}
