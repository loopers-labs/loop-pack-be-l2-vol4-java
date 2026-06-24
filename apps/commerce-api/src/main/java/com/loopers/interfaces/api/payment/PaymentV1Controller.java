package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.payment.dto.PaymentCallbackV1Request;
import com.loopers.interfaces.api.payment.dto.PaymentV1Request;
import com.loopers.interfaces.api.payment.dto.PaymentV1Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @Override
    public ApiResponse<PaymentV1Response> requestPayment(@LoginUser AuthUser authUser, @Valid @RequestBody PaymentV1Request request) {
        PaymentInfo info = paymentFacade.requestPayment(authUser.id(), request.orderId(), request.cardType(), request.cardNo());
        return ApiResponse.success(PaymentV1Response.from(info));
    }

    @PostMapping("/callback")
    @Override
    public ApiResponse<Object> handleCallback(@RequestBody PaymentCallbackV1Request request) {
        paymentFacade.handleCallback(request.transactionKey(), request.status(), request.reason());
        return ApiResponse.success();
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<PaymentV1Response> getStatus(@LoginUser AuthUser authUser, @PathVariable Long orderId) {
        PaymentInfo info = paymentFacade.getStatus(authUser.id(), orderId);
        return ApiResponse.success(PaymentV1Response.from(info));
    }
}
