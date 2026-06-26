package com.loopers.interfaces.api.payment;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "Loopers 결제 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(
        summary = "결제 요청",
        description = "주문에 대해 카드 결제를 요청한다. 외부 PG 와 연동하며, 결과는 보통 결제 대기(PENDING)로 콜백/폴링으로 확정된다.",
        parameters = {
            @Parameter(name = "X-Loopers-LoginId", in = ParameterIn.HEADER, required = true, description = "로그인 ID"),
            @Parameter(name = "X-Loopers-LoginPw", in = ParameterIn.HEADER, required = true, description = "비밀번호")
        }
    )
    ApiResponse<PaymentV1Dto.PayResponse> pay(
        @Parameter(hidden = true) User user,
        @Schema(description = "결제 요청") PaymentV1Dto.PayRequest request
    );

    @Operation(
        summary = "결제 콜백 수신",
        description = "PG가 결제 최종 상태를 통보하는 콜백을 수신해, 해당 결제와 주문의 상태를 확정한다. 이미 확정된 결제는 멱등하게 무시한다."
    )
    ApiResponse<Object> handleCallback(@Schema(description = "결제 콜백") PaymentV1Dto.CallbackRequest request);
}
