package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment Callback API", description = "PG 결제 결과 콜백(웹훅) API")
public interface PaymentCallbackV1ApiSpec {

    @Operation(
        summary = "결제 결과 콜백 수신",
        description = "PG 가 비동기 결제 결과(SUCCESS/FAILED)를 통보하는 웹훅. transactionKey 로 결제를 찾아 결과를 확정한다. "
            + "중복·순서뒤바뀜 통보에도 멱등하게 동작한다."
    )
    ApiResponse<Object> handle(PaymentV1Dto.CallbackRequest request);
}
