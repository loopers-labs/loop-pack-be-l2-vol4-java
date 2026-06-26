package com.loopers.infrastructure.payment.scheduler;

import com.loopers.application.payment.PaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 콜백 누락/타임아웃 대비 — PENDING(키 보유) 결제건을 주기적으로 PG 조회해 정정한다.
 * 테스트 프로파일에서는 비활성(@Profile "!test")하여 통합 테스트와 간섭하지 않는다.
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentFacade paymentFacade;

    @Scheduled(
        initialDelayString = "${pg.recovery.initial-delay:60000}",
        fixedDelayString = "${pg.recovery.fixed-delay:30000}"
    )
    public void reconcilePendingPayments() {
        paymentFacade.reconcileAll();
    }
}
