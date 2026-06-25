package com.loopers.interfaces.scheduling;

import com.loopers.application.payment.PaymentRecovery;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentRecovery paymentRecovery;

    /**
     * transactionKey 를 받은 채 멈춘 결제를 key 로 복구한다. (콜백 유실 대비)
     */
    @Scheduled(fixedDelayString = "${payment-recovery.scan-interval}")
    public void recoverKeyedStuckPayments() {
        paymentRecovery.recoverStuckByKey();
    }

    /**
     * key 를 못 받은 채 멈춘 결제를 orderId 로 복구한다. (동기 타임아웃·서킷 강등 대비)
     */
    @Scheduled(fixedDelayString = "${payment-recovery.scan-interval}")
    public void recoverKeylessStuckPayments() {
        paymentRecovery.recoverStuckByOrder();
    }
}
