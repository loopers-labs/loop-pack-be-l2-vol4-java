package com.loopers.interfaces.api.payment;

import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "PENDING 주문에 PG 결제 요청 — transactionKey 반환")
    ApiResponse<PaymentV1Dto.PayResponse> pay(PaymentV1Dto.PayRequest request, UserModel user);

    @Operation(summary = "결제 결과 콜백", description = "PG 처리 완료 후 콜백 수신 — status 기준 주문 확정/실패 처리")
    ApiResponse<Void> callback(PaymentV1Dto.CallbackPayload payload);
}
