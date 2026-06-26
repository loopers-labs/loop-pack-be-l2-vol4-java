package com.loopers.infrastructure.payment;

public class PgPaymentV1Dto {

    public record PaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}

    public record TransactionResponse(
        String transactionKey,
        TransactionStatus status,
        String reason
    ) {}

    public enum TransactionStatus {
        PENDING,
        SUCCESS,
        FAILED,
    }

    /** PG 응답 공통 봉투: { meta: { result, errorCode, message }, data: T } */
    public record PgResponse<T>(Metadata meta, T data) {
        public record Metadata(Result result, String errorCode, String message) {
            public enum Result { SUCCESS, FAIL }
        }
    }
}
