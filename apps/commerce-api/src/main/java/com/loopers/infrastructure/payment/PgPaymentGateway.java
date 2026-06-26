package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayCommand;
import com.loopers.domain.payment.GatewayLookup;
import com.loopers.domain.payment.GatewayResult;
import com.loopers.domain.payment.GatewayStatus;
import com.loopers.domain.payment.PaymentGateway;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PgPaymentGateway implements PaymentGateway {

    private final PgClient pgClient;

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestFallback")
    @Retry(name = "pgRequestRetry")
    @Override
    public GatewayResult requestPayment(GatewayCommand command) {
        String transactionKey = pgClient.requestPayment(command);
        return GatewayResult.accepted(transactionKey);
    }

    /**
     * 서킷 OPEN(CallNotPermittedException)은 PG에 호출조차 못 간 미접수 확정 → REJECTED(즉시 실패 처리).
     * 그 외(타임아웃·IO)는 접수 여부 불명 → PENDING 유지, 폴링/복구가 확정한다.
     */
    private GatewayResult requestFallback(GatewayCommand command, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return GatewayResult.rejected();
        }
        return GatewayResult.pending();
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "queryFallback")
    @Retry(name = "pgRetry")
    @Override
    public Optional<GatewayStatus> queryStatus(String transactionKey, Long userId) {
        PgTransactionResponse tx = pgClient.getTransactionStatus(transactionKey, userId);
        return Optional.of(new GatewayStatus(tx.status(), tx.reason()));
    }

    /** PG 조회 실패(장애 등) — 이번 복구 주기는 건너뛰고 다음 주기에 재시도. */
    private Optional<GatewayStatus> queryFallback(String transactionKey, Long userId, Throwable t) {
        return Optional.empty();
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "queryByOrderIdFallback")
    @Retry(name = "pgRetry")
    @Override
    public GatewayLookup queryByOrderId(Long orderId, Long userId) {
        List<PgTransactionResponse> transactions = pgClient.findByOrderId(orderId, userId);
        if (transactions.isEmpty()) {
            return GatewayLookup.notFound();
        }
        return selectRepresentative(transactions);
    }

    /**
     * 한 주문에 거래가 여러 개일 수 있어(요청 재시도로 인한 이중 접수 등) 결정적으로 하나를 고른다.
     * SUCCESS 우선(돈이 빠진 거래) → 없으면 PENDING(처리 중, 다음 주기 대기) → 전부 FAILED면 실패.
     * SUCCESS가 둘 이상이면 이중 청구이므로 격리 로그를 남긴다(사람이 환불 점검).
     */
    private GatewayLookup selectRepresentative(List<PgTransactionResponse> transactions) {
        List<PgTransactionResponse> success = transactions.stream()
            .filter(tx -> "SUCCESS".equals(tx.status()))
            .toList();
        if (!success.isEmpty()) {
            if (success.size() > 1) {
                log.error("PG 이중 결제 감지 — 한 주문에 SUCCESS 거래 {}건. 수동 환불 점검 필요: {}",
                    success.size(), success.stream().map(PgTransactionResponse::transactionKey).toList());
            }
            return toFound(success.get(0));
        }
        return transactions.stream()
            .filter(tx -> "PENDING".equals(tx.status()))
            .findFirst()
            .map(this::toFound)
            .orElseGet(() -> toFound(transactions.get(0)));
    }

    private GatewayLookup toFound(PgTransactionResponse tx) {
        return GatewayLookup.found(tx.transactionKey(), tx.status(), tx.reason());
    }

    /** PG 장애로 거래 유무를 알 수 없음 — UNREACHABLE로 보고 취소하지 않는다(다음 주기). */
    private GatewayLookup queryByOrderIdFallback(Long orderId, Long userId, Throwable t) {
        return GatewayLookup.unreachable();
    }
}
