package com.loopers.payment.interfaces.api;

import com.loopers.payment.application.PaymentFacade;
import com.loopers.payment.application.PaymentInfo;
import com.loopers.shared.presentation.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(request.toCommand(userId));
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }
}
