package com.loopers.infrastructure.payment.pg;

public class PgPaymentDto {

    /**
     * pg-simulator 공통 응답 envelope.
     *
     * <p>pg-simulator 는 모든 응답을 {@code {"meta": {...}, "data": {...}}} 로 감싸므로,
     * 실제 페이로드는 {@link #data} 에 들어있다. 클라이언트는 {@code data()} 로 언랩한다.
     */
    public record PgApiResponse<T>(Meta meta, T data) {
        public record Meta(String result, String errorCode, String message) {}
    }

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
