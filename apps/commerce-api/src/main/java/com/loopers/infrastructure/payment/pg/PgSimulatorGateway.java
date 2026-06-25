package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgGateway;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;


/**
 * pg-simulator 연동 구현체.
 *
 * <h2>재시도 전략 (총 3회)</h2>
 *
 * <p>pg-simulator 는 멱등키를 지원하지 않는다. 예외 종류와 무관하게 TID 생성 여부를 알 수 없으므로,
 * 모든 예외에서 재시도 전 orderId 로 기존 트랜잭션을 먼저 조회한다.
 *
 * <ul>
 *   <li>SUCCESS/PENDING 존재 → 해당 TID 반환 (콜백 대기)</li>
 *   <li>없거나 모두 FAILED → 재시도 안전</li>
 * </ul>
 *
 * <h2>CircuitBreaker</h2>
 * <p>pg-simulator 가 완전히 다운됐을 때 재시도 루프 자체를 차단해 fast-fail 한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PgSimulatorGateway implements PgGateway {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_WAIT_MS = 300L;

    private final PgSimulatorClient pgSimulatorClient;

    @CircuitBreaker(name = "pg-simulator", fallbackMethod = "requestPaymentFallback")
    @Override
    public String requestPayment(String userId, Long orderId, CardType cardType, String cardNo, Long amount, String callbackUrl) throws Exception {
        PgPaymentDto.PaymentRequest request = new PgPaymentDto.PaymentRequest(
            orderId.toString(), cardType.name(), cardNo, amount, callbackUrl);

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return pgSimulatorClient.request(userId, request).transactionKey();

            } catch (Exception e) {
                // 예외 종류 불문 — TID 생성 여부 불명. orderId 조회 후 판단
                lastException = e;
                log.warn("[PG] {}차 예외 — orderId 조회. orderId={}, cause={}", attempt, orderId, e.getMessage());

                Optional<String> existingTid = findActiveTid(userId, orderId.toString());
                if (existingTid.isPresent()) {
                    log.info("[PG] 기존 트랜잭션 확인 — 재시도 생략. orderId={}, transactionKey={}", orderId, existingTid.get());
                    return existingTid.get();
                }
                // SUCCESS/PENDING 없음 → 재시도 안전
            }

            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_WAIT_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 중단", ie);
                }
            }
        }

        log.warn("[PG] 최대 재시도({}) 소진. orderId={}", MAX_ATTEMPTS, orderId);
        throw lastException;
    }

    @Override
    public List<PgGateway.PgTransactionResult> findTransactionsByOrderId(String userId, String orderId) {
        try {
            return pgSimulatorClient.getByOrderId(userId, orderId).transactions().stream()
                .map(t -> new PgGateway.PgTransactionResult(t.transactionKey(), t.status(), t.reason()))
                .toList();
        } catch (FeignException.NotFound e) {
            return List.of();
        } catch (Exception e) {
            log.warn("[PG] orderId 조회 실패. orderId={}, cause={}", orderId, e.getMessage());
            return List.of();
        }
    }

    private Optional<String> findActiveTid(String userId, String orderId) {
        List<PgGateway.PgTransactionResult> txList = findTransactionsByOrderId(userId, orderId);
        return txList.stream()
            .filter(t -> "SUCCESS".equals(t.status()))
            .map(PgGateway.PgTransactionResult::transactionKey)
            .findFirst()
            .or(() -> txList.stream()
                .filter(t -> "PENDING".equals(t.status()))
                .map(PgGateway.PgTransactionResult::transactionKey)
                .findFirst());
    }

    private String requestPaymentFallback(String userId, Long orderId, CardType cardType, String cardNo, Long amount, String callbackUrl, Throwable t) {
        log.warn("[PG-CB] CB open. orderId={}, cause={}", orderId, t.getMessage());
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 시스템을 일시적으로 이용할 수 없습니다.");
    }
}
