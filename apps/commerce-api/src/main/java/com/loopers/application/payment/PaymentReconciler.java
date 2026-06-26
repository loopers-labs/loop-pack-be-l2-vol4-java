package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayRouter;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 결제 폴링 / 타임아웃 reconciler.
 *
 *  - 매 30초마다 IN_PROGRESS / UNKNOWN Payment 를 조회해 PG 의 최신 상태로 확정한다.
 *  - 콜백이 유실되어도 안전망이 되어, 사용자 응답은 빠르게 (PROCESSING) 돌려주고 결과는 확정되는 흐름을 만든다.
 *  - 15분 초과 결제는 강제 FAILED 로 전이 (재고 복구 이벤트 자동 발행).
 *
 * 단순화: backoff 가 아닌 fixed delay. 운영 환경에선 exponential backoff + Payment 에 다음 시도 시각 컬럼 권장.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentReconciler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentGatewayRouter router;

    @Value("${payment.polling.initial-delay}")
    private Duration initialDelay;

    @Value("${payment.timeout}")
    private Duration timeout;

    /**
     * 매 30초 polling. fixedDelay 는 직전 실행 종료 후 30초 (overlap 방지).
     */
    @Scheduled(fixedDelayString = "PT30S")
    public void reconcile() {
        ZonedDateTime now = ZonedDateTime.now();
        // 갓 생성된 결제까지 잡지 않도록 initial-delay 만큼 지난 것만 대상.
        ZonedDateTime threshold = now.minus(initialDelay);

        List<Payment> targets = paymentRepository.findReconciliationTargets(threshold);
        if (targets.isEmpty()) {
            return;
        }
        log.debug("[Reconciler] 대상 {}건 처리 시작", targets.size());

        for (Payment payment : targets) {
            try {
                processOne(payment, now);
            } catch (Exception e) {
                // 개별 실패가 전체 폴링을 막지 않도록 흡수
                log.error("[Reconciler] 처리 실패. paymentId={}, error={}", payment.getId(), e.getMessage());
            }
        }
    }

    private void processOne(Payment payment, ZonedDateTime now) {
        // 타임아웃 우선 — 15분 초과면 PG 상태 묻지 않고 강제 FAILED + 재고 복구
        if (payment.getCreatedAt().isBefore(now.minus(timeout))) {
            log.warn("[Reconciler] 타임아웃 → FAILED. paymentId={}, createdAt={}", payment.getId(), payment.getCreatedAt());
            paymentService.markFailed(payment.getId(), "결제 타임아웃 (" + timeout + " 초과)");
            return;
        }

        // UNKNOWN 인데 transactionKey 가 없으면 = PG 호출 자체가 실패한 케이스 → 폴링 불가, 타임아웃까지 대기
        if (payment.getTransactionKey() == null) {
            log.debug("[Reconciler] transactionKey 없음 — 타임아웃까지 대기. paymentId={}", payment.getId());
            return;
        }

        // PG 에 상태 조회 — gateway 의 getStatus 는 멱등 + Resilience4j 적용
        try {
            PaymentGateway gateway = router.gatewayFor(payment.getProvider());
            PgResponse response = gateway.getStatus(payment.getTransactionKey(), payment.getUserId());

            switch (response.status()) {
                case SUCCESS -> {
                    log.debug("[Reconciler] PG SUCCESS → markSuccess. paymentId={}", payment.getId());
                    paymentService.markSuccess(payment.getId());
                }
                case FAILED -> {
                    log.debug("[Reconciler] PG FAILED → markFailed. paymentId={}", payment.getId());
                    paymentService.markFailed(payment.getId(), response.reason());
                }
                case PENDING -> log.debug("[Reconciler] PG 아직 PENDING — 다음 폴링 대기. paymentId={}", payment.getId());
            }
        } catch (Exception e) {
            // 일시적 통신 실패 / CB Open 등 → 다음 폴링까지 대기 (타임아웃이 안전망)
            log.debug("[Reconciler] 일시 실패 — 다음 폴링. paymentId={}, error={}", payment.getId(), e.getMessage());
        }

        // 위 catch 의 PaymentStatus.REQUESTED 안전 처리 (이 분기엔 도달하지 않아야 하지만 가드)
        if (payment.getStatus() == PaymentStatus.REQUESTED && payment.getTransactionKey() != null) {
            log.warn("[Reconciler] REQUESTED 인데 transactionKey 존재 — 일관성 점검 필요. paymentId={}", payment.getId());
        }
    }
}
