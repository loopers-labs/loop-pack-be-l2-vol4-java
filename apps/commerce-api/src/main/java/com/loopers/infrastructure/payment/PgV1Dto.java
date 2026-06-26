package com.loopers.infrastructure.payment;

import java.util.List;

/**
 * pg-simulator 의 원시 요청/응답 계약. 이 타입들은 인프라 계층에만 머문다.
 */
public final class PgV1Dto {

    private PgV1Dto() {
    }

    public record PaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {
    }

    public record TransactionResponse(
        String transactionKey,
        String status,
        String reason
    ) {
    }

    public record TransactionDetailResponse(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {
    }

    public record OrderResponse(
        String orderId,
        List<TransactionResponse> transactions
    ) {
    }
}
