package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        AuthHeaders auth,
        @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(
            auth.loginId(), request.orderId(), request.cardType(), request.cardNo());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/callback")
    @Override
    public ApiResponse<Object> handleCallback(@RequestBody PaymentV1Dto.PgCallbackRequest request) {
        // 페이로드는 신뢰하지 않음 — 거래키만 넘기고 진실은 PG 재조회(reconcile)로 확정
        paymentFacade.handleCallback(request.transactionKey());
        return ApiResponse.success();
    }

    @PostMapping("/{paymentId}/reconcile")
    @Override
    public ApiResponse<Object> reconcile(AuthHeaders auth, @PathVariable("paymentId") Long paymentId) {
        paymentFacade.reconcile(auth.loginId(), paymentId);
        return ApiResponse.success();
    }
}
