package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 관련 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대해 PG 카드 결제를 요청한다. 접수되면 PROCESSING, PG 장애 시 PENDING 으로 정상 응답한다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(Long userId, PaymentV1Dto.PaymentRequest request);
}
