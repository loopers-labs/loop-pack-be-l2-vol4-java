package com.loopers.domain.payment;

/**
 * PG 접수(acknowledge) 응답. 동기 호출로 받는 것은 최종 결과가 아니라 transactionKey + 접수 상태(PENDING)다.
 * 실제 승인/실패(SUCCESS/FAILED)는 이후 콜백·상태조회로 확정된다.
 */
public record PaymentGatewayResult(
    String transactionKey,
    PaymentStatus status
) {
}
