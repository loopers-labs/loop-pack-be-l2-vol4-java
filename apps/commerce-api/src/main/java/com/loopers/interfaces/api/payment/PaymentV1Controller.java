package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(
            loginId, request.orderId(), request.cardType(), request.cardNo());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/callback")
    public ApiResponse<Object> handleCallback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.handleCallback(request.transactionKey(), request.status(), request.reason());
        return ApiResponse.success();
    }
}
