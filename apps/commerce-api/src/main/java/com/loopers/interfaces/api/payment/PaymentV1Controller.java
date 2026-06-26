package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> pay(
        @RequestBody @Valid PaymentV1Dto.PaymentRequest request,
        HttpServletRequest httpRequest
    ) {
        UserModel user = (UserModel) httpRequest.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(
            paymentFacade.pay(user.getId(), request.toCommand())
        ));
    }

    /** PG 콜백 수신 (인증 없음 - WebMvcConfig 에서 제외). */
    @PostMapping("/callback")
    public ApiResponse<Object> callback(@RequestBody @Valid PaymentV1Dto.CallbackRequest request) {
        paymentFacade.handleCallback(request.transactionKey(), request.status(), request.reason());
        return ApiResponse.success();
    }

    /** 수동 동기화: 콜백이 오지 않은 결제건을 PG 와 재조정한다. */
    @PostMapping("/{paymentId}/sync")
    public ApiResponse<PaymentV1Dto.PaymentResponse> sync(
        @PathVariable Long paymentId,
        HttpServletRequest httpRequest
    ) {
        UserModel user = (UserModel) httpRequest.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(
            paymentFacade.syncPayment(user.getId(), paymentId)
        ));
    }
}
