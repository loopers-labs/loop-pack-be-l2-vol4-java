package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentConfirmation;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PG 가 호출하는 콜백(웹훅) 엔드포인트. 액터가 사용자가 아니라 PG 시스템이라 인증을 요구하지 않는다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments/callback")
public class PaymentCallbackV1Controller implements PaymentCallbackV1ApiSpec {

    private final PaymentConfirmation paymentConfirmation;

    @PostMapping
    @Override
    public ApiResponse<Object> handle(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentConfirmation.confirm(request.transactionKey(), request.paymentStatus(), request.reason());
        return ApiResponse.success();
    }
}
