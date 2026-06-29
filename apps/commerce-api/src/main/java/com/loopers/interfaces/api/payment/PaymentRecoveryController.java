package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentRecoveryService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 결제 상태 수동 복구 트리거. (스케줄러와 동일 로직을 즉시 실행)
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentRecoveryController {

    private final PaymentRecoveryService paymentRecoveryService;

    @PostMapping("/recover")
    public ApiResponse<Map<String, Integer>> recover() {
        int recovered = paymentRecoveryService.recoverStuckPayments();
        return ApiResponse.success(Map.of("recovered", recovered));
    }
}
