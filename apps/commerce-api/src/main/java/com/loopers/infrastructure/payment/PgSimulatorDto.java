package com.loopers.infrastructure.payment;

/**
 * PG-Simulator 와 주고받는 직렬화용 DTO. 우리 도메인 객체와 분리해 외부 스펙 변경의 영향을 최소화한다.
 *  - 요청: PaymentApi 의 PaymentRequest 와 동일 구조
 *  - 응답: TransactionResponse / TransactionDetailResponse 의 공통 필드만
 */
public class PgSimulatorDto {

    public record Request(
        String orderId,         // PG validate 가 6자 이상 String 을 요구 → 어댑터에서 zero-padding
        String cardType,        // SAMSUNG / KB / HYUNDAI
        String cardNo,
        Long amount,
        String callbackUrl
    ) {
    }

    public record TransactionData(
        String transactionKey,
        String status,          // PENDING / SUCCESS / FAILED
        String reason
    ) {
    }

    private PgSimulatorDto() {
    }
}
