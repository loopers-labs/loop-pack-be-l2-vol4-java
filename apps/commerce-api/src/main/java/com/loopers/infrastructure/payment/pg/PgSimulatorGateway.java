package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgGateway;
import com.loopers.domain.payment.PgIndeterminateException;
import com.loopers.domain.payment.PgRequestRejectedException;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * pg-simulator 연동 구현체.
 *
 * <h2>재시도 전략 — "거부(500)만 즉시 재시도"</h2>
 *
 * <p>pg-simulator 의 요청 거부는 트랜잭션 생성 <em>이전</em>에 HTTP 500 으로 발생한다.
 * 따라서 500 은 "트랜잭션 미생성"이 보장되어 멱등키 없이도 즉시 재시도가 안전하다.
 * 최대 {@link #MAX_ATTEMPTS}회까지 재시도하고, 모두 소진되면
 * {@link PgRequestRejectedException}(확정 실패) 을 던진다.
 *
 * <p><strong>회로 open</strong>({@link CallNotPermittedException})은 CB 가 호출을 실행하기 전에
 * 차단하므로 요청이 PG 로 나가지 않는다 → 트랜잭션 미생성이 확정된다. 따라서 미확정이 아니라
 * {@link PgRequestRejectedException}(확정 실패)으로 처리해 즉시 실패·자원 복구한다.
 *
 * <p>오직 <strong>타임아웃·연결 오류</strong>(요청은 나갔으나 응답 미수신)만 트랜잭션 생성 여부가
 * 불확실하므로 <strong>재시도하지 않고</strong> {@link PgIndeterminateException}(미확정) 을 던진다.
 * 호출자는 이를 받아 결제를 실패 처리하지 않고 콜백/대사 스케줄러의 보정에 맡긴다.
 *
 * <h2>CircuitBreaker</h2>
 * <p>{@link PgCircuitClient#requestOnce} 1회 = CB 1 call. 각 재시도가 개별 호출로 집계되어
 * 개별 호출 실패율이 그대로 회로 차단 판단에 반영된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PgSimulatorGateway implements PgGateway {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_WAIT_MS = 300L;

    private final PgCircuitClient pgCircuitClient;
    private final PgSimulatorClient pgSimulatorClient;

    @Override
    public String requestPayment(String userId, Long orderId, CardType cardType, String cardNo, Long amount, String callbackUrl) {
        PgPaymentDto.PaymentRequest request = new PgPaymentDto.PaymentRequest(
            orderId.toString(), cardType.name(), cardNo, amount, callbackUrl);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                PgPaymentDto.TransactionResponse response = pgCircuitClient.requestOnce(userId, request);
                String transactionKey = response == null ? null : response.transactionKey();
                if (transactionKey == null) {
                    // 200 인데 응답/TID 가 비어있음 — 결과 미확정으로 간주
                    throw new PgIndeterminateException("PG 응답에 transactionKey 가 없습니다.", null);
                }
                return transactionKey;

            } catch (FeignException.InternalServerError e) {
                // HTTP 500 = 트랜잭션 미생성 보장 → 즉시 재시도 안전
                log.warn("[PG] {}/{}차 요청 거부(500). orderId={}", attempt, MAX_ATTEMPTS, orderId);
                if (attempt == MAX_ATTEMPTS) {
                    throw new PgRequestRejectedException(
                        "PG 요청 거부(500) " + MAX_ATTEMPTS + "회 소진 — 트랜잭션 미생성 확정", e);
                }
                sleepBeforeRetry();

            } catch (CallNotPermittedException e) {
                // 회로 open = CB 가 호출을 실행하기 전에 차단 → 요청이 PG 로 나가지 않음 →
                // 트랜잭션 미생성 확정(미확정 아님). 즉시 실패 + 자원 복구가 안전하다.
                // (재시도는 500 일 때만 하므로, 이 지점에 도달했다면 이전 시도들도 전부 미생성이다.)
                log.warn("[PG] 서킷 open — 요청 미전송, 실패 확정. orderId={}", orderId);
                throw new PgRequestRejectedException("PG 서킷 open — 요청 미전송(트랜잭션 미생성 확정)", e);

            } catch (FeignException e) {
                // 타임아웃·연결오류 등 — 트랜잭션 생성 여부 불확실, 재시도 금지
                log.warn("[PG] 응답 미확정(타임아웃/연결오류). orderId={}, cause={}", orderId, e.getMessage());
                throw new PgIndeterminateException("PG 응답 미확정: " + e.getMessage(), e);
            }
        }

        // 도달 불가 — 마지막 500 시도는 위에서 PgRequestRejectedException 으로 종료된다
        throw new PgIndeterminateException("PG 요청 처리 불가", null);
    }

    @Override
    public List<PgGateway.PgTransactionResult> findTransactionsByOrderId(String userId, String orderId) {
        try {
            PgPaymentDto.OrderTransactionResponse data = pgSimulatorClient.getByOrderId(userId, orderId).data();
            if (data == null || data.transactions() == null) {
                return List.of();
            }
            return data.transactions().stream()
                .map(t -> new PgGateway.PgTransactionResult(t.transactionKey(), t.status(), t.reason()))
                .toList();
        } catch (FeignException.NotFound e) {
            // PG 가 "기록 없음"으로 응답 — 조회는 성공한 것. 빈 리스트로 정상 처리.
            return List.of();
        } catch (RetryableException e) {
            // 타임아웃·연결오류 = PG 에 닿지도 못함. 빈 리스트로 둔갑시키면 만료 시점에 실제 SUCCESS 건이
            // FAILED 로 오종결될 수 있다. "조회 불가"를 전파해 대사 측이 이번 틱 종결을 보류하게 한다.
            log.warn("[PG] orderId 조회 불가(타임아웃/연결오류) — 종결 보류. orderId={}, cause={}", orderId, e.getMessage());
            throw new PgIndeterminateException("PG 조회 불가: " + e.getMessage(), e);
        } catch (FeignException e) {
            // PG 가 HTTP 오류로 응답(예: 해당 주문 거래 없음) — PG 는 살아있으므로 기록 없음으로 간주.
            log.warn("[PG] orderId 조회 — PG HTTP {} 응답, 기록 없음 처리. orderId={}", e.status(), orderId);
            return List.of();
        } catch (Exception e) {
            // 예상치 못한 오류 — 조회 결과를 신뢰할 수 없으니 빈 리스트로 단정하지 않고 보류시킨다.
            log.warn("[PG] orderId 조회 중 예외 — 종결 보류. orderId={}, cause={}", orderId, e.getMessage());
            throw new PgIndeterminateException("PG 조회 중 예외: " + e.getMessage(), e);
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_WAIT_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new PgIndeterminateException("재시도 대기 중단", ie);
        }
    }
}
