package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment API", description = "결제 API")
public interface PaymentV1ApiSpec {

    @Operation(
        summary = "결제 요청",
        description = "주문에 대해 카드 결제를 외부 PG 로 접수한다. 동기 응답은 접수증(PENDING + transactionKey)이며, "
            + "최종 결과(SUCCESS/FAILED)는 이후 콜백·상태조회로 확정된다."
    )
    ApiResponse<PaymentV1Dto.PaymentResponse> pay(LoginUser loginUser, PaymentV1Dto.PaymentRequest request);
}
