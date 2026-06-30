package com.loopers.application.payment;

import java.util.List;

/**
 * PG(pg-simulator) 연동 DTO. PG 응답은 {meta, data} envelope 로 래핑돼 온다.
 * 우리 interfaces.api.ApiResponse 를 재사용하지 않는다(application→interfaces 역의존 방지).
 */
public final class PgDto {

    private PgDto() {}

    /** 우리 Order id(Long) → PG orderId(문자열, 6자 이상 규칙). */
    public static String orderId(Long id) {
        return String.format("%06d", id);
    }

    public record Envelope<T>(Meta meta, T data) {
        public record Meta(String result, String errorCode, String message) {}

        public boolean isSuccess() {
            return meta != null && "SUCCESS".equals(meta.result());
        }
    }

    /** PG 결제 요청 바디 (PG 스펙: orderId 6자 이상, cardNo xxxx-xxxx-xxxx-xxxx, amount 양수, callbackUrl prefix 검증). */
    public record PaymentRequest(String orderId, String cardType, String cardNo, Long amount, String callbackUrl) {}

    /** PG 결제 요청 응답 (동기): transactionKey + status(PENDING) + reason. */
    public record TransactionResponse(String transactionKey, String status, String reason) {}

    /** PG 거래 상세 조회 응답 (상태확인 폴링용). */
    public record TransactionDetailResponse(
        String transactionKey, String orderId, String cardType, String cardNo, Long amount, String status, String reason) {}

    /** PG 주문별 거래 조회 응답 (타임아웃으로 키를 못 받은 경우 입양용). */
    public record OrderResponse(String orderId, List<TransactionResponse> transactions) {}
}
