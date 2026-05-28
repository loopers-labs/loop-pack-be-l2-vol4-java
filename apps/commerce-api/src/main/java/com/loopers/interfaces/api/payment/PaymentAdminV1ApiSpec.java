package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment Admin V1 API", description = "결제 PG 콜백 어드민 API — HMAC 서명 검증 필요")
public interface PaymentAdminV1ApiSpec {

    @Operation(summary = "결제 확정", description = "PG 결제 성공 콜백 — HMAC 검증 후 주문 확정 + 재고 차감 + 결제 기록")
    ApiResponse<PaymentV1Dto.PaymentResponse> confirm(String signature, String rawBody);

    @Operation(summary = "결제 실패", description = "PG 결제 실패 콜백 — HMAC 검증 후 주문 실패 처리 + 재고 해제 + 결제 기록")
    ApiResponse<PaymentV1Dto.PaymentResponse> fail(String signature, String rawBody);
}
