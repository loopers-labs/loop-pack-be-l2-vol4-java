package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** ⚠️ 멀티 인스턴스에서는 단일 실행 보장(분산락/배치)이 필요하다. 현재는 단일 실행 전제. */
@Slf4j
@Profile("!test")
@RequiredArgsConstructor
@Component
public class PaymentRecoveryScheduler {

    private final PaymentRecoveryService paymentRecoveryService;

    @Scheduled(fixedDelayString = "${payment.recovery.fixed-delay-ms:30000}")
    public void recover() {
        // 두 복구는 독립적이다 — 한쪽이 실패해도 다른 쪽은 수행한다.
        try {
            paymentRecoveryService.reconcilePending();
        } catch (Exception e) {
            log.error("결제 복구 실패: reconcilePending", e);
        }
        try {
            paymentRecoveryService.recoverKeyless();
        } catch (Exception e) {
            log.error("결제 복구 실패: recoverKeyless", e);
        }
        try {
            paymentRecoveryService.reapplySuccess();
        } catch (Exception e) {
            log.error("결제 복구 실패: reapplySuccess", e);
        }
        try {
            paymentRecoveryService.flagStuck();
        } catch (Exception e) {
            log.error("결제 복구 실패: flagStuck", e);
        }
    }
}
