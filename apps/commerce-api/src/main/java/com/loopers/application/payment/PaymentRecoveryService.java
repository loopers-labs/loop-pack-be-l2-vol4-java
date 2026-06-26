package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgClient.PgTransactionStatus;
import com.loopers.domain.payment.PgClient.PgTransactionView;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 복구 — 콜백 유실 / 타임아웃 결제건을 PG GET API 로 동기화한다.
 * <p>
 * 두 가지 경로:
 * <ul>
 *   <li>TIMEOUT_PENDING: PG 요청 직후 응답을 못 받아 transactionKey 가 없을 수 있음 → orderId 로 PG 검색 → 발견되면 attach 후 상태 동기화</li>
 *   <li>REQUESTED (오래된): transactionKey 있음 → PG GET (by key) → 종료 상태면 markSucceeded/markFailed</li>
 * </ul>
 * <p>
 * 멱등 보장: PgClient 폴백이 빈 결과를 반환 → 동기화 스킵. 다음 주기에 재시도. 진척 없는 결제는 자연스럽게 누적.
 */
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pg.recovery.enabled", havingValue = "true", matchIfMissing = false)
@Component
public class PaymentRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryService.class);

    private final PaymentService paymentService;
    private final PaymentFacade paymentFacade;
    private final PgClient pgClient;

    @Value("${pg.recovery.requested-stale-after:30s}")
    private Duration requestedStaleAfter;

    @Value("${pg.recovery.batch-size:50}")
    private int batchSize;

    /**
     * 주기 실행 — fixedDelay 로 이전 실행 종료 후 N 밀리초 대기 (겹침 방지).
     */
    @Scheduled(fixedDelayString = "${pg.recovery.fixed-delay-ms:5000}", initialDelay = 10_000)
    public void recoverRecoverablePayments() {
        ZonedDateTime threshold = ZonedDateTime.now().minus(requestedStaleAfter);
        List<PaymentModel> targets = paymentService.findRecoverable(threshold, batchSize);
        if (targets.isEmpty()) return;
        log.info("결제 복구 시작 — 대상 {}건", targets.size());

        int succeeded = 0;
        int failed = 0;
        int stillPending = 0;

        for (PaymentModel payment : targets) {
            Outcome outcome = recoverOne(payment);
            switch (outcome) {
                case SUCCEEDED -> succeeded++;
                case FAILED -> failed++;
                case STILL_PENDING -> stillPending++;
            }
        }
        log.info("결제 복구 완료 — succeeded={}, failed={}, stillPending={}", succeeded, failed, stillPending);
    }

    private Outcome recoverOne(PaymentModel payment) {
        try {
            if (payment.getStatus() == PaymentStatus.TIMEOUT_PENDING && payment.getTransactionKey() == null) {
                // 거래 키 없음 — orderId 로 PG 검색해 attach 시도
                List<PgTransactionView> found = pgClient.findByOrderId(payment.getOrderId());
                if (found.isEmpty()) {
                    return Outcome.STILL_PENDING;
                }
                PgTransactionView view = found.get(0);
                paymentService.attachTransactionKey(payment.getId(), view.transactionKey());
                return applyView(payment.getId(), view);
            }

            // 거래 키 있음 — PG GET 으로 단건 조회
            String key = payment.getTransactionKey();
            if (key == null) {
                return Outcome.STILL_PENDING;
            }
            Optional<PgTransactionView> view = pgClient.getByTransactionKey(key);
            if (view.isEmpty()) {
                return Outcome.STILL_PENDING;
            }
            return applyView(payment.getId(), view.get());
        } catch (Exception e) {
            log.warn("결제 복구 중 예외 — 다음 주기에 재시도. paymentId={}, cause={}",
                payment.getId(), e.getMessage());
            return Outcome.STILL_PENDING;
        }
    }

    private Outcome applyView(Long paymentId, PgTransactionView view) {
        if (view.status() == PgTransactionStatus.SUCCESS) {
            paymentFacade.applyExternalStatus(paymentId, "SUCCESS", view.reason());
            return Outcome.SUCCEEDED;
        }
        if (view.status() == PgTransactionStatus.FAILED) {
            paymentFacade.applyExternalStatus(paymentId, "FAILED", view.reason());
            return Outcome.FAILED;
        }
        return Outcome.STILL_PENDING;
    }

    enum Outcome { SUCCEEDED, FAILED, STILL_PENDING }
}
