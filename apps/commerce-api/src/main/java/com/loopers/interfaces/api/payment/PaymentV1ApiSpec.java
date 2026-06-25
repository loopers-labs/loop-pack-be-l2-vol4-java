package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 도메인 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(
        summary = "결제 요청",
        description = "주문에 대해 PG 카드 결제를 요청합니다. 응답은 접수(PENDING)이며, 최종 결과는 콜백/상태조회로 확정됩니다."
    )
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(AuthHeaders auth, PaymentV1Dto.PaymentRequest request);
}
