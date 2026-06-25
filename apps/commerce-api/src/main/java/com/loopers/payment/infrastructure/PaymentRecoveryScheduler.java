package com.loopers.payment.infrastructure;

import com.loopers.payment.application.PaymentRecoveryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
    name = "commerce.payment.recovery.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class PaymentRecoveryScheduler {

    private final PaymentRecoveryFacade paymentRecoveryFacade;

    @Scheduled(fixedDelayString = "${commerce.payment.recovery.fixed-delay-ms:5000}")
    public void recover() {
        paymentRecoveryFacade.recoverDuePayments();
    }
}
