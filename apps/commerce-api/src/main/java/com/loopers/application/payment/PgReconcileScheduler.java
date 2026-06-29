package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PgGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * pg-simulator 비동기 결제 대사 스케줄러 — PAYMENT_IN_PROGRESS 보정의 단일 책임자.
 *
 * <p>점유 완료(PAYMENT_IN_PROGRESS) 후 콜백을 못 받은 주문을 매 틱 pg-simulator 에 orderId 로
 * 조회해 최종 상태를 확정한다. (Toss 동기 흐름의 PAYMENT_IN_PROGRESS 보정도 이 스케줄러로 일원화)
 *
 * <h2>판정 기준</h2>
 * <ul>
 *   <li><strong>SUCCESS 있음</strong> → 주문 완료 처리 (콜백 유실 복구)</li>
 *   <li><strong>PENDING 없고 FAILED 있음</strong> → 실패 처리 + 자원 복구</li>
 *   <li><strong>미확정(PENDING 또는 기록 없음)</strong>
 *     <ul>
 *       <li>{@code paymentStartedAt + 만료시간} 이전 → 다음 틱 재확인</li>
 *       <li>만료시간 초과 → 더 못 기다리고 FAILED + 자원 복구로 종결</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>콜백·재시도와 경합해도 {@link PaymentApplicationService#handleCallback} 의 {@code isTerminal()}
 * 가드 + 주문 행 비관적 락이 중복 처리를 막는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PgReconcileScheduler {

    /** 미확정 건을 더 기다리지 않고 FAILED 로 종결하는 만료 기준 (paymentStartedAt 기준). */
    private static final int RESOLVE_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final PgGateway pgGateway;
    private final PaymentApplicationService paymentApplicationService;

    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        List<OrderModel> inProgress = orderRepository.findByStatus(OrderStatus.PAYMENT_IN_PROGRESS);

        for (OrderModel order : inProgress) {
            try {
                reconcileOne(order, now);
            } catch (Exception e) {
                log.warn("[PgReconcile] 처리 실패 — orderId={}", order.getId(), e);
            }
        }
    }

    private void reconcileOne(OrderModel order, ZonedDateTime now) {
        Long orderId = order.getId();
        String userId = order.getUserId().toString();

        List<PgGateway.PgTransactionResult> txList = pgGateway.findTransactionsByOrderId(userId, orderId.toString());

        // 1. SUCCESS 우선 — 콜백 유실 복구
        Optional<PgGateway.PgTransactionResult> success = txList.stream()
            .filter(t -> "SUCCESS".equals(t.status()))
            .findFirst();
        if (success.isPresent()) {
            paymentApplicationService.handleCallback(success.get().transactionKey(), orderId, "SUCCESS", null);
            log.info("[PgReconcile] 결제 성공 확인 → 주문 완료. orderId={}", orderId);
            return;
        }

        // 2. PENDING 이 하나라도 있으면 아직 처리 중 — 만료 검사로 넘어감
        boolean hasPending = txList.stream().anyMatch(t -> "PENDING".equals(t.status()));

        // 3. PENDING 없고 FAILED 있음 → 실패 확정
        if (!hasPending) {
            Optional<PgGateway.PgTransactionResult> failed = txList.stream()
                .filter(t -> "FAILED".equals(t.status()))
                .findFirst();
            if (failed.isPresent()) {
                paymentApplicationService.handleCallback(failed.get().transactionKey(), orderId, "FAILED", failed.get().reason());
                log.info("[PgReconcile] 결제 실패 확인 → 자원 복구. orderId={}, reason={}", orderId, failed.get().reason());
                return;
            }
        }

        // 4. 미확정(PENDING 또는 기록 없음) — 만료 데드라인 검사
        // PAYMENT_IN_PROGRESS 면 startPayment 에서 항상 설정되지만, 데이터 이상 시 NPE 대신 다음 틱으로 미룬다.
        ZonedDateTime startedAt = order.getPaymentStartedAt();
        if (startedAt == null) {
            log.warn("[PgReconcile] paymentStartedAt 누락 — 만료 판정 보류. orderId={}", orderId);
            return;
        }
        ZonedDateTime deadline = startedAt.plusMinutes(RESOLVE_MINUTES);
        if (now.isAfter(deadline)) {
            paymentApplicationService.handleCallback(null, orderId, "FAILED", "PG 결제 결과 미확인(만료)");
            log.info("[PgReconcile] 만료 종결 → 자원 복구. orderId={}", orderId);
        } else {
            log.debug("[PgReconcile] 미확정 — 다음 틱 재확인. orderId={}", orderId);
        }
    }
}
