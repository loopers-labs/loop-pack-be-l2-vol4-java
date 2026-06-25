package com.loopers.interfaces.api.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.utils.DateTimeUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;
    private final DateTimeUtil dateTimeUtil;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentV1Dto.PaymentResponse> createPayment(
        @Valid @RequestBody PaymentV1Dto.CreateRequest request,
        @LoginUser AuthenticatedUser loginUser
    ) {
        PaymentInfo paymentInfo = paymentFacade.createPayment(
            loginUser.userId(),
            request.orderId(),
            request.cardType(),
            request.cardNo(),
            dateTimeUtil.now()
        );

        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentInfo));
    }

    @Override
    @PostMapping("/callback")
    public ApiResponse<Void> handleCallback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.handleCallback(Long.parseLong(request.orderId()), request.status(), request.reason());

        return ApiResponse.success();
    }
}
