package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
    prefix = "loopers.payment",
    name = "reconciliation-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class PaymentReconciliationScheduler {

    private final PaymentService paymentService;
    private final PaymentStatusSynchronizer paymentStatusSynchronizer;
    private final PaymentProperties paymentProperties;

    @Scheduled(fixedDelayString = "${loopers.payment.reconciliation-delay:10s}")
    public void reconcilePendingPayments() {
        for (Payment payment : paymentService.findPendingPaymentsForReconciliation(paymentProperties.reconciliationBatchSize())) {
            try {
                paymentStatusSynchronizer.syncPayment(payment.getUserLoginId(), payment.getOrderId());
            } catch (RuntimeException exception) {
                log.warn(
                    "Pending payment reconciliation failed. paymentId={}, orderId={}",
                    payment.getId(),
                    payment.getOrderId(),
                    exception
                );
            }
        }
    }
}
