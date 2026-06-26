package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Payment V1 API", description = "Loopers 결제 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대한 결제를 PG 로 요청한다. 결과는 비동기로 확정되며 PENDING 으로 응답한다(202).")
    ApiResponse<PaymentV1Dto.PaymentResponse> pay(
            @RequestHeader String loginId, @RequestHeader String loginPw, PaymentV1Dto.PaymentRequest request);

    @Operation(summary = "결제 조회", description = "결제의 현재 상태를 조회한다(클라이언트 폴링). 소유자만 접근 가능하다.")
    ApiResponse<PaymentV1Dto.PaymentStatusResponse> get(
            @RequestHeader String loginId, @RequestHeader String loginPw, Long paymentId);

    @Operation(summary = "결제 콜백 수신", description = "PG 가 처리 결과를 통보하는 공개 엔드포인트(로그인 헤더 없음). 무결성 가드 후 상태를 확정한다.")
    ApiResponse<Void> callback(PaymentV1Dto.CallbackRequest request);

    @Operation(summary = "결제 수동 복구", description = "운영자가 UNKNOWN/오래된 PENDING 결제를 강제 재조회·확정한다(관리자).")
    ApiResponse<PaymentV1Dto.ReconcileResponse> reconcile(String ldap, Long paymentId);
}
