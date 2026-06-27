package com.loopers.infrastructure.pg;

public class PgApiResponse {

    public record Payment(String transactionKey, String status, String reason) {}

    public record PaymentStatus(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {}

    public record PaymentStatusWithOrderId(String orderId, java.util.List<Payment> transactions) {}
}
