package com.loopers.infrastructure.payment;

/**
 * PG 시뮬레이터의 JSON 계약과 1:1 로 대응하는 infrastructure 전용 DTO. 도메인은 이 타입들을 모른다.
 */
final class PgPaymentDto {

    private PgPaymentDto() {}

    record Request(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {
    }

    record ApiResponse<T>(
        Meta meta,
        T data
    ) {
        record Meta(
            String result,
            String errorCode,
            String message
        ) {
        }
    }

    record Response(
        String transactionKey,
        String status,
        String reason
    ) {
    }

    record TransactionDetail(
        String status,
        String reason
    ) {
    }
}
