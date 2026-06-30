package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * 콜백 누락 안전망: grace(10s) 넘게 PENDING 인 결제만 골라 주기적으로 PG 에 조회해 보정한다(폴링).
 * 전수조사가 아니라 "콜백이 왔어야 하는데 안 온" 건만 — 처리지연(1~5s) 위로 grace 를 둔다.
 * 한 건 실패가 전체를 막지 않도록 건별로 격리한다.
 */
@RequiredArgsConstructor
@Component
public class PaymentReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconcileScheduler.class);
    private static final Duration GRACE = Duration.ofSeconds(10);

    private final PaymentRepository paymentRepository;
    private final PaymentReconciler reconciler;

    @Scheduled(fixedDelayString = "${pg.reconcile.interval-ms:10000}")
    public void reconcilePending() {
        ZonedDateTime threshold = ZonedDateTime.now().minus(GRACE);
        paymentRepository.findPendingOlderThan(threshold).forEach(payment -> {
            try {
                reconciler.reconcile(payment);
            } catch (Exception e) {
                log.warn("[reconcile] 결제 보정 실패 paymentId={} : {}", payment.getId(), e.toString());
            }
        });
    }
}
