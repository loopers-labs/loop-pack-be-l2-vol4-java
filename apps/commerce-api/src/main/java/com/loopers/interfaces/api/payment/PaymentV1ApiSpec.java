package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Operation(summary = "결제 콜백 수신", description = "PG 콜백 수신. 본문 status를 신뢰하지 않고 PG 재조회로 검증한다.")
    ApiResponse<Object> handleCallback(@RequestBody PaymentV1Dto.CallbackRequest request);

    @Operation(summary = "결제 상태 수동 동기화", description = "transactionKey로 PG를 재조회해 결제·주문 상태를 정정한다.")
    ApiResponse<Object> sync(@PathVariable("transactionKey") String transactionKey);
}
