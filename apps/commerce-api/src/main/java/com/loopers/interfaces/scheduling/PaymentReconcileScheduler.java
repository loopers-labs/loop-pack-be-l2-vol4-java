package com.loopers.interfaces.scheduling;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentReconcileResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PENDING 결제 자동 정리(reconcile) 스케줄러 (03 §3.5). PG 콜백이 유실돼 PENDING으로 남은 결제를 주기적으로
 * PG 진실원천과 대조해 끝내 확정한다. 수동 트리거({@code AdminPaymentV1Controller})와 동일한
 * {@link PaymentFacade#reconcilePending}를 호출하는, 운영 안전망 성격의 인바운드 어댑터다.
 * <p>
 * 멀티 인스턴스에서 같은 회차가 중복 실행되지 않도록 {@link SchedulerLock}으로 한 인스턴스만 잡게 한다
 * (락 백엔드/시간 기준은 {@code SchedulerConfig}). 한 회차는 PENDING의 첫 페이지({@code size}건)만 처리하며,
 * 남은 건은 다음 회차로 넘어간다 — 콜백이 정상 동작하면 PENDING은 거의 없으므로 안전망으로 충분하다.
 * <p>
 * 통합 테스트(profile {@code test})에서는 스케줄이 실제로 발화해 PG를 호출하지 않도록 빈 등록을 제외한다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class PaymentReconcileScheduler {

    private final PaymentFacade paymentFacade;

    @Value("${payment.reconcile.page-size:100}")
    private int pageSize;

    /**
     * 직전 실행 종료 후 {@code fixed-delay} 만큼 쉬고 다음 회차를 돈다(겹침 방지). {@code lockAtMostFor}는 작업이
     * 비정상 종료해도 다른 인스턴스가 그만큼 뒤 재실행하게 하는 상한, {@code lockAtLeastFor}는 매우 빨리 끝났을 때도
     * 최소 그만큼 락을 유지해 시계 차로 인한 동시 재실행을 막는 하한이다.
     */
    @Scheduled(
        fixedDelayString = "${payment.reconcile.fixed-delay:60000}",
        initialDelayString = "${payment.reconcile.initial-delay:30000}")
    @SchedulerLock(
        name = "paymentReconcile",
        lockAtMostFor = "${payment.reconcile.lock-at-most-for:PT2M}",
        lockAtLeastFor = "${payment.reconcile.lock-at-least-for:PT5S}")
    public void reconcilePending() {
        LockAssert.assertLocked();
        PaymentReconcileResult result = paymentFacade.reconcilePending(0, pageSize);
        // PENDING이 하나도 없으면(정상 상황) 로그 소음을 줄이고, 실제 정리/잔여가 있을 때만 남긴다.
        if (result.scanned() > 0) {
            log.info("결제 reconcile 실행 — scanned={}, paid={}, failed={}, stillPending={}, skipped={}",
                result.scanned(), result.paid(), result.failed(), result.stillPending(), result.skipped());
        }
    }
}
