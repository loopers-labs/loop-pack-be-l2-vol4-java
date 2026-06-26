package com.loopers.interfaces.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * status·reason은 위·변조 가능한 콜백 본문이라 신뢰하지 않는다 — 실제 상태는 PG 재조회로 확정하므로
 * handleCallback에서 의도적으로 사용하지 않고, 수신 트리거인 transactionKey만 검증한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCallbackV1Request(
    @NotBlank String transactionKey,
    String status,
    String reason
) {
}
