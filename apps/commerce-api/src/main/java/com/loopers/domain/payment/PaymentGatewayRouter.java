package com.loopers.domain.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 멀티 PG 라우팅. 등록된 {@link PaymentGateway} 들 중 사용 가능한 어댑터를 선택한다.
 *
 *  - Circuit Breaker 가 Open 인 PG 는 후보에서 제외 (장애 PG 우회).
 *  - 6주차에서는 PG_SIMULATOR 하나만 등록되어 있어 사실상 항상 그 어댑터를 반환하지만,
 *    구조가 멀티 PG 확장에 열려 있다.
 *  - 향후 카드사 기반 라우팅 (예: SAMSUNG → Toss, KB → Kakao) 도 같은 메서드에 정책만 추가하면 됨.
 *
 * 사용 가능한 PG 가 하나도 없으면 {@link PgPermanentException} 을 던져 호출자가 즉시 FAILED 처리하도록 한다
 * (UNKNOWN 으로 둘 수 없음 — transactionKey 가 없어 폴링도 불가).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentGatewayRouter {

    private final List<PaymentGateway> gateways;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * 결제 요청 어댑터 선택. 등록된 어댑터 순서대로 평가하며,
     * 첫 번째로 사용 가능한(=Circuit Breaker 가 Open 이 아닌) 어댑터를 반환한다.
     */
    public PaymentGateway select(Payment payment) {
        return gateways.stream()
            .filter(this::isAvailable)
            .findFirst()
            .orElseThrow(() -> {
                log.error("[PG Router] 사용 가능한 PG 가 없습니다 (모두 Circuit Breaker Open). paymentId={}", payment.getId());
                return new PgPermanentException("사용 가능한 PG 가 없습니다.");
            });
    }

    private boolean isAvailable(PaymentGateway gateway) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(gateway.cbName());
        CircuitBreaker.State state = cb.getState();
        if (state == CircuitBreaker.State.OPEN) {
            log.warn("[PG Router] CircuitBreaker Open — provider={} skip", gateway.provider());
            return false;
        }
        return true;
    }

    /**
     * 특정 PG 에 대응하는 어댑터를 반환. 폴링(Reconciler) 에서 Payment 가 어떤 PG 로 결제됐는지에 따라
     * 해당 PG 의 getStatus 를 호출해야 한다.
     */
    public PaymentGateway gatewayFor(PgProvider provider) {
        return gateways.stream()
            .filter(gw -> gw.provider() == provider)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "[PG Router] 등록된 PG 없음. provider=" + provider));
    }
}
