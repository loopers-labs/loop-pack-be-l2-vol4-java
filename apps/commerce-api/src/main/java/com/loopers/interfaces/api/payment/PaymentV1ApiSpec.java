package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Payment", description = "결제 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대해 PG 카드 결제를 요청한다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody PaymentV1Dto.PaymentRequest request
    );

    @Operation(summary = "결제 콜백 수신", description = "PG가 비동기 결제 결과를 통지하는 콜백 엔드포인트.")
    ApiResponse<Object> handleCallback(@RequestBody PaymentV1Dto.CallbackRequest request);
}
