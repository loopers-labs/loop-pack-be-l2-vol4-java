package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미확정(PENDING) 결제 복구 스케줄러.
 * ⚠️ 멀티 인스턴스에서는 단일 실행 보장이 필요하다(분산락/배치 모듈). 현재는 단일 실행 전제.
 */
@Profile("!test")
@RequiredArgsConstructor
@Component
public class PaymentRecoveryScheduler {

    private final PaymentRecoveryService paymentRecoveryService;

    @Scheduled(fixedDelayString = "${payment.recovery.fixed-delay-ms:30000}")
    public void recover() {
        paymentRecoveryService.reconcilePending();
        paymentRecoveryService.recoverKeyless();
    }
}
