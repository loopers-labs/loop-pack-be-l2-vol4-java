package com.loopers.payment.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Payment V1 API", description = "Loopers 결제 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(
            summary = "결제 요청",
            description = "인증된 사용자가 주문에 대해 카드 결제를 요청합니다. PENDING 결제를 만들고 PG 에 결제를 요청한 뒤 "
                    + "접수 결과(PENDING)를 반환합니다. 최종 성공/실패는 PG 콜백으로 확정됩니다."
    )
    ApiResponse<PaymentV1Response.Accepted> pay(
            @Parameter(hidden = true) Long userId,
            @Valid PaymentV1Request.Pay request
    );
}
