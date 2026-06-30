package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
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
            @Valid @RequestBody PaymentV1Dto.CreateRequest request,
            @RequestAttribute("authenticatedUserId") Long userId
    ) {
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(
                paymentFacade.createPayment(request.orderNumber(), userId, request.cardType(), request.cardNo())
        ));
    }

    @PostMapping("/{transactionKey}/sync")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> syncPayment(
            @PathVariable String transactionKey,
            @RequestAttribute("authenticatedUserId") Long userId
    ) {
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentFacade.syncPayment(transactionKey, userId)));
    }

    @PostMapping("/callback")
    @Override
    public ApiResponse<Void> callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.handleCallback(request.transactionKey(), request.status());
        return ApiResponse.success(null);
    }

}
