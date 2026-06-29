package com.loopers.infrastructure.payment;

import java.util.List;

/**
 * pg-simulator 의 요청/응답 계약을 그대로 본뜬 Feign 전용 DTO.
 * 도메인 모델(PgRequestResult 등)과 분리한다 — PG가 바뀌면 이 파일만 영향받게.
 * 응답 래퍼는 pg-simulator 의 ApiResponse(meta+data) 구조와 동일하다.
 */
public class PgClientDto {

    /** PG 공통 응답 래퍼 */
    public record ApiResponse<T>(Metadata meta, T data) {
        public record Metadata(String result, String errorCode, String message) {}
    }

    /** POST /api/v1/payments 요청 바디 */
    public record PaymentRequest(
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {}

    /** POST 응답 data / 주문조회의 transactions 항목 (reason 은 null 이면 생략될 수 있음) */
    public record TransactionResponse(
            String transactionKey,
            String status,
            String reason
    ) {}

    /** GET /api/v1/payments/{txKey} 응답 data */
    public record TransactionDetailResponse(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {}

    /** GET /api/v1/payments?orderId= 응답 data */
    public record OrderResponse(
            String orderId,
            List<TransactionResponse> transactions
    ) {}
}
