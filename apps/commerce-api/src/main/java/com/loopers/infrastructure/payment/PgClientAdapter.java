package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgRequestException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PG 도메인 포트의 인프라 어댑터.
 * <p>
 * Resilience4j 적용:
 * - request: @CircuitBreaker + @Retry (3회, 지수 백오프) → fallback(requestFallback)
 *   - 폴백은 PgRequestException 을 던져 호출자(Facade) 가 TIMEOUT_PENDING 으로 전이하도록 신호
 *   - GET 계열은 멱등하므로 재시도가 안전. POST 도 PG 시뮬레이터 명세상 멱등키(orderId) 가 있어 안전 가정
 * - getByTransactionKey / findByOrderId: @CircuitBreaker 만 (조회는 재시도 안전, 폴백은 Optional.empty / 빈 리스트)
 * <p>
 * @CircuitBreaker name="pg" — 모든 메서드를 같은 회로로 묶어 PG 시스템 전체 장애를 감지.
 */
@RequiredArgsConstructor
@Component
public class PgClientAdapter implements PgClient {

    private static final Logger log = LoggerFactory.getLogger(PgClientAdapter.class);
    private static final String CB_NAME = "pg";
    private static final String SYSTEM_USER_ID = "0"; // PG 시뮬레이터는 X-USER-ID 헤더만 요구

    private final PgFeignClient pgFeignClient;

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "requestFallback")
    @Retry(name = CB_NAME)
    public PgRequestResponse request(PgRequestCommand command) {
        PgFeignDto.RequestPayload payload = new PgFeignDto.RequestPayload(
            String.valueOf(command.orderId()),
            command.cardType().name(),
            command.cardNo(),
            String.valueOf(command.amount()),
            command.callbackUrl()
        );
        PgFeignDto.PgResponse<PgFeignDto.RequestResponse> response =
            pgFeignClient.request(headerUserId(command.userId()), payload);

        if (response == null || response.meta() == null || !response.meta().isSuccess() || response.data() == null) {
            throw new PgRequestException(
                "PG 결제 요청이 실패 응답을 반환했습니다: " + (response == null || response.meta() == null
                    ? "(no meta)" : response.meta().message()));
        }
        return new PgRequestResponse(response.data().transactionKey(), parseStatus(response.data().status()));
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getByKeyFallback")
    public Optional<PgTransactionView> getByTransactionKey(String transactionKey) {
        PgFeignDto.PgResponse<PgFeignDto.TransactionView> response =
            pgFeignClient.getByTransactionKey(SYSTEM_USER_ID, transactionKey);
        if (response == null || response.meta() == null || !response.meta().isSuccess() || response.data() == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(response.data()));
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "findByOrderIdFallback")
    public List<PgTransactionView> findByOrderId(Long orderId) {
        PgFeignDto.PgResponse<PgFeignDto.TransactionListView> response =
            pgFeignClient.findByOrderId(SYSTEM_USER_ID, String.valueOf(orderId));
        if (response == null || response.meta() == null || !response.meta().isSuccess()
            || response.data() == null || response.data().transactions() == null) {
            return Collections.emptyList();
        }
        return response.data().transactions().stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * request 폴백 — 모든 예외(TimeoutException, FeignException, CallNotPermittedException 포함) 에서 호출.
     * 호출자(Facade) 가 TIMEOUT_PENDING 으로 전이할 수 있도록 PgRequestException 으로 변환한다.
     */
    @SuppressWarnings("unused")
    private PgRequestResponse requestFallback(PgRequestCommand command, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.warn("PG 회로가 열려 있어 요청이 차단되었습니다. orderId={}", command.orderId());
        } else {
            log.warn("PG 요청 실패 — 폴백으로 진입합니다. orderId={}, cause={}",
                command.orderId(), t == null ? "unknown" : t.getClass().getSimpleName());
        }
        throw new PgRequestException(
            "PG 결제 요청이 지연/실패하여 복구 대기 상태로 전환합니다.", t);
    }

    @SuppressWarnings("unused")
    private Optional<PgTransactionView> getByKeyFallback(String transactionKey, Throwable t) {
        log.warn("PG 단건 조회 실패 — 빈 결과 반환. transactionKey={}, cause={}",
            transactionKey, t == null ? "unknown" : t.getClass().getSimpleName());
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    private List<PgTransactionView> findByOrderIdFallback(Long orderId, Throwable t) {
        log.warn("PG 주문 조회 실패 — 빈 리스트 반환. orderId={}, cause={}",
            orderId, t == null ? "unknown" : t.getClass().getSimpleName());
        return Collections.emptyList();
    }

    private PgTransactionView toDomain(PgFeignDto.TransactionView view) {
        return new PgTransactionView(
            view.transactionKey(),
            view.orderId() == null ? null : Long.parseLong(view.orderId()),
            parseStatus(view.status()),
            view.reason()
        );
    }

    private static PgTransactionStatus parseStatus(String raw) {
        if (raw == null) return PgTransactionStatus.PENDING;
        return switch (raw.toUpperCase()) {
            case "SUCCESS" -> PgTransactionStatus.SUCCESS;
            case "FAILED", "FAIL", "INVALID_CARD", "LIMIT_EXCEEDED" -> PgTransactionStatus.FAILED;
            default -> PgTransactionStatus.PENDING;
        };
    }

    private static String headerUserId(Long userId) {
        return userId == null ? SYSTEM_USER_ID : String.valueOf(userId);
    }
}
