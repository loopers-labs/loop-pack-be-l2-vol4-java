package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.common.interceptor.AuthInterceptor;
import com.loopers.interfaces.api.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @Override
    public ApiResponse<PaymentV1Dto.PayResponse> pay(
        @RequestBody @Valid PaymentV1Dto.PayRequest request,
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user
    ) {
        String transactionKey = paymentFacade.requestPayment(request.orderId(), user.getId(), request.cardType(), request.cardNo());
        return ApiResponse.success(new PaymentV1Dto.PayResponse(transactionKey));
    }

    @PostMapping("/callback")
    @Override
    public ApiResponse<Void> callback(@RequestBody PaymentV1Dto.CallbackPayload payload) {
        UUID orderId = UUID.fromString(payload.orderId());
        if ("SUCCESS".equals(payload.status())) {
            paymentFacade.confirm(orderId, payload.transactionKey(), payload.amount());
        } else {
            paymentFacade.fail(orderId, payload.transactionKey(), payload.amount());
        }
        return ApiResponse.success(null);
    }
}
