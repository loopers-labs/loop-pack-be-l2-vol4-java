package com.loopers.interfaces.api.payment;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.payment.PaymentReconciliationService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.utils.DateTimeUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/payments")
public class PaymentAdminV1Controller implements PaymentAdminV1ApiSpec {

    private final PaymentReconciliationService paymentReconciliationService;
    private final DateTimeUtil dateTimeUtil;

    @Override
    @PostMapping("/{orderId}/reconcile")
    public ApiResponse<PaymentAdminV1Dto.ReconcileResponse> reconcilePayment(@PathVariable Long orderId) {
        PaymentStatus status = paymentReconciliationService.reconcileByOrderId(orderId, dateTimeUtil.now());

        return ApiResponse.success(PaymentAdminV1Dto.ReconcileResponse.of(orderId, status));
    }
}
