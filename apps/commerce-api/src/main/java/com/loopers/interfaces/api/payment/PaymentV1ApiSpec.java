package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.payment.dto.PaymentCallbackV1Request;
import com.loopers.interfaces.api.payment.dto.PaymentV1Request;
import com.loopers.interfaces.api.payment.dto.PaymentV1Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "Loopers 결제 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대해 PG 결제를 접수합니다. 결과는 콜백/폴링으로 확정됩니다.")
    ApiResponse<PaymentV1Response> requestPayment(AuthUser authUser, PaymentV1Request request);

    @Operation(summary = "결제 콜백 수신", description = "PG가 결제 처리 결과를 통보합니다.")
    ApiResponse<Object> handleCallback(PaymentCallbackV1Request request);

    @Operation(summary = "결제 상태 조회", description = "주문 기준으로 현재 결제 상태를 조회합니다(폴링).")
    ApiResponse<PaymentV1Response> getStatus(AuthUser authUser, Long orderId);
}
