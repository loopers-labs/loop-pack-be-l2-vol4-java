package com.loopers.infrastructure.payment;

public class PgSimulatorDto {

    public record PaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {
    }

    public record ApiResponse<T>(
        Metadata meta,
        T data
    ) {

        public record Metadata(
            String result,
            String errorCode,
            String message
        ) {
        }
    }

    public record TransactionResponse(
        String transactionKey,
        String status,
        String reason
    ) {
    }
}
