package com.loopers.payment.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment Callback V1 API", description = "PG 결제 결과 콜백 수신 API 입니다.")
public interface PaymentCallbackV1ApiSpec {

    @Operation(
            summary = "결제 결과 콜백 수신",
            description = "PG 가 최종 결과(SUCCESS/FAILED)를 통보한다. transactionKey 로 결제를 찾아 진위 검증 후 "
                    + "Payment/Order 상태를 확정한다(멱등). URL 의 provider 로 어느 PG 의 콜백인지 구분한다."
    )
    ApiResponse<Object> callback(String provider, PaymentCallbackV1Request.Callback request);
}
