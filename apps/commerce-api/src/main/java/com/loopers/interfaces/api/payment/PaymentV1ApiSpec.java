package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대한 결제를 요청합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> createPayment(PaymentV1Dto.CreateRequest request);

    @Operation(summary = "결제 승인", description = "결제를 승인합니다. 승인 시 주문이 완료 처리됩니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> approve(Long paymentId);

    @Operation(summary = "결제 실패", description = "결제를 실패 처리합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> fail(Long paymentId);

    @Operation(summary = "결제 만료", description = "결제를 만료 처리합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> expire(Long paymentId);
}
