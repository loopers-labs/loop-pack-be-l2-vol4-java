package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.loopers.domain.payment.PaymentStatus;

/**
 * pg-simulator 와 주고받는 페이로드. PG 의 응답 래퍼는 {meta,data} 형태이며, 우리는 data 만 사용한다.
 */
public final class PgPaymentDto {

    private PgPaymentDto() {
    }

    /** PG 응답 공통 래퍼. data 만 추출한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope<T>(T data) {
    }

    /** PG 결제 요청 바디. cardType 은 enum 이름(SAMSUNG/KB/HYUNDAI) 문자열로 전송한다. */
    public record Request(
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {
    }

    /** POST /payments 응답 data. */
    public record TransactionResponse(
            String transactionKey,
            String status,
            String reason
    ) {

        public PaymentStatus toStatus() {
            return PaymentStatus.valueOf(status);
        }
    }

    /** GET /payments/{key} 응답 data. */
    public record TransactionDetailResponse(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {

        public PaymentStatus toStatus() {
            return PaymentStatus.valueOf(status);
        }
    }
}
