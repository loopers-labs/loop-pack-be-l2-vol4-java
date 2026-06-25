package com.loopers.application.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PG 호출에 회복 전략을 적용하는 얇은 래퍼. @CircuitBreaker 가 Feign 호출(PgClient)을 감싸고,
 * 실패/지연/서킷오픈 시 fallback 으로 "미확정" envelope 를 돌려준다(예외 흡수 → 내부 정상 응답).
 * Timeout 은 Feign read-timeout(3s)으로, 느린 응답은 slow-call(2s)로 잡힌다.
 * fallback 은 추측으로 FAILED 단정하지 않는다 — 결제는 PENDING 으로 남고 이후 reconcile 로 진실을 맞춘다.
 */
@RequiredArgsConstructor
@Component
public class PgPaymentClient {

    private final PgClient pgClient;

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestPaymentFallback")
    public PgDto.Envelope<PgDto.TransactionResponse> requestPayment(String userId, PgDto.PaymentRequest request) {
        return pgClient.requestPayment(userId, request);
    }

    @SuppressWarnings("unused") // resilience4j fallbackMethod (리플렉션 호출)
    private PgDto.Envelope<PgDto.TransactionResponse> requestPaymentFallback(
        String userId, PgDto.PaymentRequest request, Throwable t) {
        return new PgDto.Envelope<>(
            new PgDto.Envelope.Meta("FAIL", "PG_UNAVAILABLE", "결제 시스템이 일시적으로 불안정합니다."),
            null);
    }
}
