package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentRecoveryScheduler {

    private final PaymentApplicationService paymentApplicationService;

    @Scheduled(fixedDelayString = "${pg.recovery.fixed-delay:30000}")
    public void recover() {
        paymentApplicationService.recoverPendingPayments();
    }
}
