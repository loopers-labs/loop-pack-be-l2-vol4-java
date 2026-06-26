package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment Admin V1 API", description = "Loopers 결제 관리자 도메인 API 입니다.")
public interface PaymentAdminV1ApiSpec {

    @Operation(
        summary = "관리자 결제 정합성 수동 복구",
        description = "관리자가 특정 주문의 결제를 외부 결제 시스템 조회로 즉시 재대사한다. 콜백 유실·격리(STUCK)된 건도 다시 확정하며, 복구 후 결제 상태를 응답한다."
    )
    ApiResponse<PaymentAdminV1Dto.ReconcileResponse> reconcilePayment(Long orderId);
}
