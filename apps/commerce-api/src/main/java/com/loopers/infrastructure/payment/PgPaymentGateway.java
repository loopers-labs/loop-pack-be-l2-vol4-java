package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayCommand;
import com.loopers.domain.payment.GatewayLookup;
import com.loopers.domain.payment.GatewayResult;
import com.loopers.domain.payment.PaymentGateway;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PgPaymentGateway implements PaymentGateway {

    private final PgClient pgClient;

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestFallback")
    @Retry(name = "pgRetry")
    @Override
    public GatewayResult requestPayment(GatewayCommand command) {
        String transactionKey = pgClient.requestPayment(command);
        return GatewayResult.accepted(transactionKey);
    }

    /** 타임아웃·서킷 Open 등 접수 결과 불명 — 실패로 단정하지 않고 PENDING 유지. 폴링/복구가 확정한다. */
    private GatewayResult requestFallback(GatewayCommand command, Throwable t) {
        return GatewayResult.pending();
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "queryFallback")
    @Retry(name = "pgRetry")
    @Override
    public Optional<String> queryStatus(String transactionKey, Long userId) {
        return Optional.of(pgClient.getTransactionStatus(transactionKey, userId));
    }

    /** PG 조회 실패(장애 등) — 이번 복구 주기는 건너뛰고 다음 주기에 재시도. */
    private Optional<String> queryFallback(String transactionKey, Long userId, Throwable t) {
        return Optional.empty();
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "queryByOrderIdFallback")
    @Retry(name = "pgRetry")
    @Override
    public GatewayLookup queryByOrderId(Long orderId, Long userId) {
        return pgClient.findByOrderId(orderId, userId)
            .map(tx -> GatewayLookup.found(tx.transactionKey(), tx.status()))
            .orElseGet(GatewayLookup::notFound);
    }

    /** PG 장애로 거래 유무를 알 수 없음 — UNREACHABLE로 보고 취소하지 않는다(다음 주기). */
    private GatewayLookup queryByOrderIdFallback(Long orderId, Long userId, Throwable t) {
        return GatewayLookup.unreachable();
    }
}
