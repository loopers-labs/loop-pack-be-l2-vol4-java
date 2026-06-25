package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.pg.PgCallbackPayload;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    /** 결제 요청 */
    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @CurrentUser UserModel currentUser,
        @Valid @RequestBody PaymentV1Dto.CreatePaymentRequest request
    ) {
        return ApiResponse.success(
            PaymentV1Dto.PaymentResponse.from(
                paymentFacade.requestPayment(request.toCommand(currentUser.getId()))
            )
        );
    }

    /** PG 콜백 수신 — AuthInterceptor 제외 대상 */
    @PostMapping("/callback")
    public ApiResponse<Void> handleCallback(@RequestBody PgCallbackPayload payload) {
        paymentFacade.handleCallback(payload);
        return ApiResponse.success(null);
    }

    /** PG 상태 수동 동기화 — 콜백 미수신 또는 타임아웃 결제건 복구용 */
    @PostMapping("/{paymentId}/sync")
    public ApiResponse<PaymentV1Dto.PaymentResponse> syncPayment(
        @CurrentUser UserModel currentUser,
        @PathVariable Long paymentId
    ) {
        return ApiResponse.success(
            PaymentV1Dto.PaymentResponse.from(
                paymentFacade.syncPayment(paymentId, currentUser.getId())
            )
        );
    }
}
