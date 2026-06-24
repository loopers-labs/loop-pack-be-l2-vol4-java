package com.loopers.interfaces.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** PG가 callbackUrl로 보내는 거래 결과. 필요한 필드만 받고 나머지(카드번호 등)는 무시한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCallbackV1Request(
    String transactionKey,
    String status,
    String reason
) {
}
