package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "Loopers 결제(PG 연동) 대고객 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(
            summary = "결제 요청",
            description = "로그인 사용자가 주문에 대해 카드 결제를 요청합니다. PG 가 비동기라 응답은 보통 PENDING 이며, "
                    + "확정은 이후 콜백/상태조회로 이뤄집니다."
    )
    ApiResponse<PaymentV1Dto.PayResponse> pay(Long userId, PaymentV1Dto.PayRequest request);

    @Operation(
            summary = "결제 상태 조회",
            description = "본인 결제건의 현재 상태를 조회합니다. 우리 DB 기준값을 반환하며 PG 를 재호출하지 않습니다. "
                    + "아직 확정 전이면 PENDING(처리중)으로 노출됩니다."
    )
    ApiResponse<PaymentV1Dto.DetailResponse> getPayment(Long userId, Long paymentId);

    @Operation(
            summary = "PG 결제 콜백 수신",
            description = "PG 가 결제 처리 결과를 통보하는 콜백 엔드포인트입니다. 멱등 처리되어 중복 콜백을 흡수합니다."
    )
    ApiResponse<Object> callback(PaymentV1Dto.CallbackRequest request);

    @Operation(
            summary = "결제 정산(수동 복구)",
            description = "콜백 미수신 등으로 PENDING 으로 남은 결제를 PG 상태조회로 되물어 확정합니다."
    )
    ApiResponse<Object> reconcile(Long paymentId);
}
