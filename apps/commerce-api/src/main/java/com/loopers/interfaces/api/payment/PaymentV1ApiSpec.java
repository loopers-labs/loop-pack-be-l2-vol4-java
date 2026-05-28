package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 PG 콜백 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 확정", description = "PG 결제 성공 콜백 — 주문 확정 + 재고 차감 + 결제 기록")
    ApiResponse<PaymentV1Dto.PaymentResponse> confirm(PaymentV1Dto.ConfirmRequest request);

    @Operation(summary = "결제 실패", description = "PG 결제 실패 콜백 — 주문 실패 처리 + 재고 해제 + 결제 기록")
    ApiResponse<PaymentV1Dto.PaymentResponse> fail(PaymentV1Dto.FailRequest request);
}
