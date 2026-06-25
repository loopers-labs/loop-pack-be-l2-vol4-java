package com.loopers.infrastructure.payment.pg;

public class PgV1Dto {

    public record PaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}

    public record PaymentResponse(Meta meta, Data data) {
        public record Meta(String result, String errorCode, String message) {}
        public record Data(String transactionKey, String status, String reason) {}
    }
}
