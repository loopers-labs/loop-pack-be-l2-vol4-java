package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "Loopers 결제 도메인 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(
        summary = "결제 요청",
        description = "회원이 본인의 주문에 대해 카드 결제를 요청한다. 결제 금액은 대상 주문의 최종 결제 금액에서 도출하며, "
            + "주문당 한 건만 접수된다. 결제를 접수 대기(PENDING)로 생성한 뒤 외부 결제 시스템에 접수를 요청하고 거래 식별자를 기록한다. "
            + "최종 결과는 이후 외부 결제 시스템의 콜백으로 확정된다."
    )
    ApiResponse<PaymentV1Dto.PaymentResponse> createPayment(
        PaymentV1Dto.CreateRequest request,
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );
}
