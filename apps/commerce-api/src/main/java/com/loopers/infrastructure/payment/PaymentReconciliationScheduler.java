package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 폴링(reconciliation) 스케줄러. 콜백이 유실돼도 PENDING 이 영원히 갇히지 않도록 주기적으로 쓸어담는다(설계 §6.3).
 * grace period(10초) 경과한 PENDING 만 대상으로 한다 — 방금 PENDING 된 건은 콜백에 일할 기회를 준다.
 * (@EnableScheduling 은 CommerceApiApplication 에 이미 선언돼 있다.)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentReconciliationScheduler {

    private static final long GRACE_SECONDS = 10;

    private final PaymentService paymentService;
    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelay = 5000)
    public void reconcilePendingPayments() {
        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(GRACE_SECONDS);
        List<PaymentModel> targets = paymentService.findPendingForReconcile(threshold);
        for (PaymentModel payment : targets) {
            try {
                paymentFacade.reconcile(payment);
            } catch (Exception e) {
                // 한 건 실패가 다음 건을 막지 않도록 격리. 다음 주기에 재시도된다.
                log.warn("결제 reconciliation 실패: paymentId={}", payment.getId(), e);
            }
        }
    }
}
