package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.support.payment.PaymentWaitingRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentApplicationService paymentApplicationService;
    private final PaymentWaitingRegistry registry;

    @PostMapping
    public CompletableFuture<ApiResponse<PaymentV1Dto.Response>> pay(
        @RequestBody PaymentV1Dto.PaymentRequest request,
        @LoginUser String userId
    ) {
        return paymentApplicationService
            .initiate(userId, request.orderId(), request.cardType(), request.cardNo())
            .thenApply(info -> ApiResponse.success(PaymentV1Dto.Response.from(info)));
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentApplicationService.processCallback(
            request.transactionKey(),
            request.status(),
            request.reason()
        );

        registry.<PaymentInfo>pop(request.transactionKey())
            .filter(f -> !f.isDone())
            .ifPresent(f -> {
                PaymentInfo info = paymentApplicationService.getPaymentByTransactionKey(request.transactionKey());
                f.complete(info);
            });

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentV1Dto.Response> getPayment(
        @PathVariable String paymentId,
        @LoginUser String userId
    ) {
        PaymentInfo info = paymentApplicationService.getPayment(userId, paymentId);
        return ApiResponse.success(PaymentV1Dto.Response.from(info));
    }
}
