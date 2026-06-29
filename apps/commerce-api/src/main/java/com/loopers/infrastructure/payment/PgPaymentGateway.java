package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayUnavailableException;
import com.loopers.domain.payment.PaymentDeclinedException;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgRequestResult;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.payment.PgTransactionResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * PaymentGateway 포트의 PG 구현(어댑터).
 * - 모든 호출을 CircuitBreaker("pg") 로 감싼다.
 * - 타임아웃·커넥션거부 등 분류되지 않은 예외는 PgServerException 으로 래핑해 CB 가 집계하게 한다.
 * - fallbackMethod: CB OPEN 또는 시스템 장애 시 호출 — 요청은 'PG 못 씀' 예외로 전환,
 *   조회는 빈 결과를 반환해 서비스가 무너지지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private final PgFeignClient pgFeignClient;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    // ===================== 결제 요청 =====================
    @Override
    @CircuitBreaker(name = "pg", fallbackMethod = "requestFallback")
    @Retry(name = "pg")   // PgServerException(타임아웃·5xx)만 재시도. CB 안쪽에서 동작(aspect-order)
    public PgRequestResult requestPayment(PgPaymentCommand command) {
        try {
            PgClientDto.PaymentRequest body = new PgClientDto.PaymentRequest(
                    pgOrderId(command.orderId()),       // PG 는 orderId 6자 이상 요구 → zero-pad
                    command.cardType().name(),
                    command.cardNo(),
                    command.amount(),
                    callbackUrl
            );
            PgClientDto.TransactionResponse data = pgFeignClient.request(body).data();
            return new PgRequestResult(data.transactionKey(), PgStatus.valueOf(data.status()));
        } catch (PgClientException | PgServerException e) {
            throw e;                                     // 이미 ErrorDecoder 가 분류함
        } catch (Exception e) {
            throw new PgServerException("PG 결제요청 호출 실패(타임아웃 등): " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private PgRequestResult requestFallback(PgPaymentCommand command, Throwable t) {
        if (t instanceof PgClientException) {
            // 비즈니스/요청 오류 → 도메인 거절 예외로 번역 (CB 와 무관, 재시도 무의미)
            throw new PaymentDeclinedException(t.getMessage());
        }
        log.warn("[PG 폴백] 결제요청 실패 orderId={}, cause={}", command.orderId(), t.toString());
        throw new PaymentGatewayUnavailableException("PG 결제 요청을 처리할 수 없습니다.", t);
    }

    // ===================== txKey 단건 조회 =====================
    @Override
    @CircuitBreaker(name = "pg", fallbackMethod = "findByKeyFallback")
    public Optional<PgTransactionResult> findByTransactionKey(String transactionKey) {
        try {
            PgClientDto.TransactionDetailResponse d = pgFeignClient.getByTransactionKey(transactionKey).data();
            return Optional.of(new PgTransactionResult(
                    d.transactionKey(), d.orderId(), PgStatus.valueOf(d.status()), d.reason(), d.amount()));
        } catch (PgClientException e) {
            return Optional.empty();                     // 404 등 → 결과 없음
        } catch (PgServerException e) {
            throw e;
        } catch (Exception e) {
            throw new PgServerException("PG 단건조회 호출 실패: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private Optional<PgTransactionResult> findByKeyFallback(String transactionKey, Throwable t) {
        log.warn("[PG 폴백] 단건조회 실패 txKey={}, cause={}", transactionKey, t.toString());
        return Optional.empty();
    }

    // ===================== orderId 목록 조회 =====================
    @Override
    @CircuitBreaker(name = "pg", fallbackMethod = "findByOrderFallback")
    public List<PgTransactionResult> findByOrderId(Long orderId) {
        try {
            PgClientDto.OrderResponse data = pgFeignClient.getByOrderId(pgOrderId(orderId)).data();
            return data.transactions().stream()
                    .map(tx -> new PgTransactionResult(
                            tx.transactionKey(), data.orderId(), PgStatus.valueOf(tx.status()), tx.reason(), 0L))
                    .toList();
        } catch (PgClientException e) {
            return List.of();                            // 404 등 → 결과 없음
        } catch (PgServerException e) {
            throw e;
        } catch (Exception e) {
            throw new PgServerException("PG 주문조회 호출 실패: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private List<PgTransactionResult> findByOrderFallback(Long orderId, Throwable t) {
        log.warn("[PG 폴백] 주문조회 실패 orderId={}, cause={}", orderId, t.toString());
        return List.of();
    }

    /** 우리 주문ID(Long)를 PG 요구사항(6자 이상)에 맞춰 zero-pad. 복호화는 Long.parseLong 으로 가능. */
    private String pgOrderId(Long orderId) {
        return String.format("%06d", orderId);
    }
}
