package com.loopers.infrastructure.payment.scheduler;

import com.loopers.application.payment.PaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * 콜백 누락/타임아웃 대비 — PENDING(키 보유) 결제건을 주기적으로 PG 조회해 정정한다.
 * 테스트 프로파일에서는 비활성(@Profile "!test")하여 통합 테스트와 간섭하지 않는다.
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentFacade paymentFacade;

    /** 미아 종결 TTL: 이 시간보다 오래된 키-없는 PENDING만 대사·만료 대상 (PG 처리·재시도·콜백 유예를 충분히 넘긴 값). */
    @Value("${pg.recovery.orphan-ttl:PT10M}")
    private Duration orphanTtl;

    /** 콜백 누락 복구: 키 보유 PENDING을 PG 재조회로 정정. */
    @Scheduled(
        initialDelayString = "${pg.recovery.initial-delay:60000}",
        fixedDelayString = "${pg.recovery.fixed-delay:30000}"
    )
    public void reconcilePendingPayments() {
        paymentFacade.reconcileAll();
    }

    /** 미아 종결: 키 안 붙은 PENDING을 orderId로 PG 대사 후 입양 또는 EXPIRED. */
    @Scheduled(
        initialDelayString = "${pg.recovery.orphan-initial-delay:120000}",
        fixedDelayString = "${pg.recovery.orphan-fixed-delay:60000}"
    )
    public void recoverOrphanPayments() {
        paymentFacade.recoverOrphans(ZonedDateTime.now().minus(orphanTtl));
    }
}
