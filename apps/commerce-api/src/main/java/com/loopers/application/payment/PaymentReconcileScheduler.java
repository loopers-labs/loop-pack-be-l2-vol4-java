package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 콜백 미수신 대비, 주기적으로 PENDING 결제를 PG 와 재조정한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentReconcileScheduler {

    private final PaymentReconciler paymentReconciler;

    @Scheduled(
        fixedDelayString = "${pg.reconcile.fixed-delay-ms:60000}",
        initialDelayString = "${pg.reconcile.initial-delay-ms:60000}"
    )
    public void reconcile() {
        paymentReconciler.reconcileAllPending();
    }
}
