package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgTransaction;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 콜백 보조 복구 경로. 미결 PENDING 중 PG 호출을 실제 시도한 건만 orderId 로 역조회해 확정한다.
 * 서킷 OPEN(PG 장애)이면 폴링을 건너뛴다 — 죽은 PG 를 두드려 장애를 증폭시키지 않기 위함.
 * 역조회로도 확정되지 않으면 복구 시도 횟수를 누적해, 상한 초과 시 폴링 대상에서 제외한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private static final String CB = "pgPayment";

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final PaymentFacade paymentFacade;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Scheduled(fixedDelay = 10_000L)
    public void recover() {
        CircuitBreaker.State state = circuitBreakerRegistry.circuitBreaker(CB).getState();
        if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
            return;
        }

        List<PaymentModel> recoverable = paymentService.findRecoverable();
        for (PaymentModel payment : recoverable) {
            try {
                Optional<PgTransaction> tx = paymentGateway.findByOrderId(payment.getUserId(), payment.getOrderId());
                if (tx.isPresent() && tx.get().status() != PaymentStatus.PENDING) {
                    reflect(payment, tx.get());
                } else {
                    // PG에 거래가 없거나 아직 PENDING 상태인경우 미해결로 1회 기록(상한 초과 시 폴링 제외 + 경보)
                    paymentService.recordRecoveryAttempt(payment.getOrderId());
                }
            } catch (RuntimeException e) {
                // 일시적 PG 오류는 로그만
                log.warn("[orderId = {}] 결제 복구 폴링 실패: {}", payment.getOrderId(), e.toString());
            }
        }
    }

    private void reflect(PaymentModel payment, PgTransaction tx) {
        paymentFacade.reflect(payment.getOrderId(), tx.transactionKey(), tx.status(), tx.amount(), tx.reason());
    }
}
