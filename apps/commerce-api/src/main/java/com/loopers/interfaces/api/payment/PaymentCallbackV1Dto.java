package com.loopers.interfaces.api.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * PG-Simulator 가 콜백으로 보내는 body. TransactionInfo 와 동일 구조이지만
 * 우리는 transactionKey / status / reason 만 사용한다.
 * 알 수 없는 필드는 무시 (PG 응답 스펙 확장 대비).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCallbackV1Dto(
    String transactionKey,
    String orderId,
    String cardType,
    String cardNo,
    Long amount,
    String status,
    String reason
) {
}
