package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> createPayment(
            @Valid @RequestBody PaymentV1Dto.CreateRequest request
    ) {
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentFacade.createPayment(request.orderId())));
    }

    @PostMapping("/{paymentId}/approve")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> approve(@PathVariable Long paymentId) {
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentFacade.approve(paymentId)));
    }

    @PostMapping("/{paymentId}/fail")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> fail(@PathVariable Long paymentId) {
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentFacade.fail(paymentId)));
    }

    @PostMapping("/{paymentId}/expire")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> expire(@PathVariable Long paymentId) {
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentFacade.expire(paymentId)));
    }
}
