package com.loopers.payment.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.payment.application.PaymentResultHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments/callback")
public class PaymentCallbackV1Controller implements PaymentCallbackV1ApiSpec {

    private final PaymentResultHandler paymentResultHandler;

    @PostMapping("/{provider}")
    @Override
    public ApiResponse<Object> callback(
            @PathVariable("provider") String provider,
            @RequestBody PaymentCallbackV1Request.Callback request
    ) {
        paymentResultHandler.handle(request.toConfirm());
        return ApiResponse.success();
    }
}
