package com.loopers.infrastructure.payment;

import java.util.List;

/**
 * PG 시뮬레이터의 HTTP DTO. 도메인에 노출되지 않는 wire-level 표현.
 */
public final class PgFeignDto {

    private PgFeignDto() {}

    public record PgResponse<T>(Meta meta, T data) {}

    public record Meta(String result, String errorCode, String message) {
        public boolean isSuccess() {
            return result != null && result.equalsIgnoreCase("SUCCESS");
        }
    }

    public record RequestPayload(
        String orderId,
        String cardType,
        String cardNo,
        String amount,
        String callbackUrl
    ) {}

    public record RequestResponse(
        String transactionKey,
        String status
    ) {}

    public record TransactionView(
        String transactionKey,
        String orderId,
        String status,
        String reason
    ) {}

    public record TransactionListView(
        List<TransactionView> transactions
    ) {}
}
