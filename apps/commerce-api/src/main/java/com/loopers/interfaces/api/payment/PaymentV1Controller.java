package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
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
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentApplicationService paymentApplicationService;

    @PostMapping
    @Override
    public ApiResponse<PaymentV1Dto.PayResponse> pay(
            @LoginUser Long userId,
            @RequestBody PaymentV1Dto.PayRequest request
    ) {
        PaymentInfo.Requested info = paymentApplicationService.pay(request.toCriteria(userId));
        return ApiResponse.success(PaymentV1Dto.PayResponse.from(info));
    }

    @GetMapping("/{paymentId}")
    @Override
    public ApiResponse<PaymentV1Dto.DetailResponse> getPayment(
            @LoginUser Long userId,
            @PathVariable("paymentId") Long paymentId
    ) {
        PaymentInfo.Detail info = paymentApplicationService.getPayment(paymentId, userId);
        return ApiResponse.success(PaymentV1Dto.DetailResponse.from(info));
    }

    @PostMapping("/callback")
    @Override
    public ApiResponse<Object> callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentApplicationService.handleCallback(request.toCriteria());
        return ApiResponse.success();
    }

    @PostMapping("/{paymentId}/reconcile")
    @Override
    public ApiResponse<Object> reconcile(@PathVariable("paymentId") Long paymentId) {
        paymentApplicationService.reconcile(paymentId);
        return ApiResponse.success();
    }
}
