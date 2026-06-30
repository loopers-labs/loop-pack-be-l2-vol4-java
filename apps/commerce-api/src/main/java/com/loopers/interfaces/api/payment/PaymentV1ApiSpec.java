package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대한 결제를 PG에 요청합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> createPayment(PaymentV1Dto.CreateRequest request, Long userId);

    @Operation(summary = "결제 상태 동기화", description = "PG에 결제 상태를 조회하여 PENDING 결제를 동기화합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> syncPayment(String transactionKey, Long userId);

    @Operation(summary = "PG 콜백", description = "PG로부터 결제 결과를 수신합니다.")
    ApiResponse<Void> callback(PaymentV1Dto.CallbackRequest request);

}
