package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            @RequestHeader(value = "X-Loopers-UserId", required = false) Long userId,
            @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-Loopers-UserId 헤더가 필요합니다.");
        }
        PaymentInfo info = paymentFacade.pay(request.toCriteria(userId));
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }
}
