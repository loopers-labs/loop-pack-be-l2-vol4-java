package com.loopers.payment.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.payment.application.PaymentFacade;
import com.loopers.payment.application.PaymentResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ApiResponse<PaymentV1Response.Accepted> pay(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PaymentV1Request.Pay request
    ) {
        PaymentResult.Accepted result = paymentFacade.pay(request.toCommand(userId));
        return ApiResponse.success(PaymentV1Response.Accepted.from(result));
    }
}
