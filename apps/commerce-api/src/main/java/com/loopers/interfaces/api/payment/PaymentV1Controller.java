package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(
            loginId, loginPw, request.orderId(), request.cardType(), request.cardNo());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/callback")
    @Override
    public ApiResponse<Object> handleCallback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        // 본문 status는 신뢰하지 않는다 — transactionKey만 사용해 PG 재조회로 검증(보안 #3)
        paymentFacade.handleCallback(request.transactionKey());
        return ApiResponse.success(null);
    }

    @PostMapping("/{transactionKey}/sync")
    @Override
    public ApiResponse<Object> sync(@PathVariable("transactionKey") String transactionKey) {
        paymentFacade.reconcile(transactionKey);
        return ApiResponse.success(null);
    }
}
