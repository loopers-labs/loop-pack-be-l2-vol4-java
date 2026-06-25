package com.loopers.interfaces.api.admin;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentReconcileResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 운영(Admin). PG 콜백 유실로 PENDING에 남은 결제를 PG 진실원천으로 재확인해 확정하는
 * reconcile 트리거를 제공한다 (03 §3.5). 한 회차에 size건씩 처리하며 결과 집계를 반환한다.
 *
 * NOTE: 현재 프로젝트에 운영자 권한 체계가 없어 다른 Admin 기능과 동일하게 인증 가드 없이 노출된다.
 *       운영 환경에서는 운영자 인증·인가 가드가 선행되어야 한다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping("/reconcile")
    public ApiResponse<PaymentReconcileResult> reconcile(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "100") int size
    ) {
        return ApiResponse.success(paymentFacade.reconcilePending(page, size));
    }
}
