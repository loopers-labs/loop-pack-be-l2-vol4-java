package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping("/confirm")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> confirm(@RequestBody @Valid PaymentV1Dto.ConfirmRequest request) {
        return ApiResponse.success(
            PaymentV1Dto.PaymentResponse.from(
                paymentFacade.confirm(request.orderId(), request.pgTransactionId(), request.amount())
            )
        );
    }

    @PostMapping("/fail")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> fail(@RequestBody @Valid PaymentV1Dto.FailRequest request) {
        return ApiResponse.success(
            PaymentV1Dto.PaymentResponse.from(
                paymentFacade.fail(request.orderId(), request.pgTransactionId(), request.amount())
            )
        );
    }
}
