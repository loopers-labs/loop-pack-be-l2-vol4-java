package com.loopers.payment.interfaces;

import com.loopers.payment.application.PaymentFacade;
import com.loopers.payment.application.PaymentInfo;
import com.loopers.support.auth.CurrentUser;
import com.loopers.support.auth.LoginUser;
import com.loopers.support.response.ApiResponse;
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

    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @CurrentUser LoginUser loginUser,
        @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(
            loginUser.id(), loginUser.loginId(),
            request.orderId(), request.cardType(), request.cardNo()
        );
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/callback")
    public ApiResponse<Void> handleCallback(
        @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        paymentFacade.handleCallback(request.transactionKey(), request.orderId());
        return ApiResponse.success(null);
    }

    @PostMapping("/{orderId}/recover")
    public ApiResponse<Void> recoverPayment(
        @CurrentUser LoginUser loginUser,
        @PathVariable Long orderId
    ) {
        paymentFacade.recoverPayment(orderId, loginUser.id(), loginUser.loginId());
        return ApiResponse.success(null);
    }
}
