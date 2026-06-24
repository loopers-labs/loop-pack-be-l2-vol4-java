package com.loopers.interfaces.scheduling;

import com.loopers.application.payment.PaymentRecovery;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentRecovery paymentRecovery;

    @Scheduled(fixedDelayString = "${payment-recovery.scan-interval}")
    public void recoverStuckPayments() {
        paymentRecovery.recoverStuck();
    }
}
