package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.LoginUser;
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

    @PostMapping
    @Override
    public ApiResponse<PaymentV1Dto.PayResponse> pay(
        @LoginUser User user,
        @RequestBody PaymentV1Dto.PayRequest request
    ) {
        return ApiResponse.success(PaymentV1Dto.PayResponse.from(
            paymentFacade.pay(user.getId(), request.orderId(), request.cardType(), request.cardNo())));
    }

    @PostMapping("/callback")
    @Override
    public ApiResponse<Object> handleCallback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.confirm(request.transactionKey(), request.status(), request.reason());
        return ApiResponse.success();
    }
}
