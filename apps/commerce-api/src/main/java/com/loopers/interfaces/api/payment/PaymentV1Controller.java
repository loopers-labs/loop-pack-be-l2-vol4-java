package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ApiResponse<PaymentDto.RequestPayment.V1.Response> requestPayment(
        @LoginUser AuthenticatedUser user,
        @Valid @RequestBody PaymentDto.RequestPayment.V1.Request request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(user.loginId(), request.toCommand(user.loginId()));
        return ApiResponse.success(PaymentDto.RequestPayment.V1.Response.from(info));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentDto.RequestPayment.V1.Response> syncPayment(
        @LoginUser AuthenticatedUser user,
        @PathVariable Long orderId
    ) {
        PaymentInfo info = paymentFacade.syncPayment(user.loginId(), orderId);
        return ApiResponse.success(PaymentDto.RequestPayment.V1.Response.from(info));
    }

    @PostMapping("/callback")
    public ApiResponse<PaymentDto.RequestPayment.V1.Response> callback(
        @Valid @RequestBody PaymentDto.Callback.V1.Request request
    ) {
        PaymentInfo info = paymentFacade.handleCallback(request.toCommand());
        return ApiResponse.success(PaymentDto.RequestPayment.V1.Response.from(info));
    }
}
