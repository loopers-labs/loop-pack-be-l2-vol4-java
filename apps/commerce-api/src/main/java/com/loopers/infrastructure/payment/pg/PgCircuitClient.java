package com.loopers.infrastructure.payment.pg;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * pg-simulator 결제 요청 1회 호출을 서킷브레이커로 감싸는 얇은 빈.
 *
 * <p><strong>왜 별도 빈인가</strong>: {@code @CircuitBreaker} 는 스프링 프록시로 동작하므로
 * 같은 클래스 내부 호출(self-invocation)에는 적용되지 않는다. 또한 CB 를 "재시도 루프 전체"가
 * 아니라 "PG 호출 1회"에 걸어야 <strong>각 재시도가 개별 호출로 집계</strong>되어,
 * 개별 호출 실패율이 그대로 회로 차단 판단에 반영된다.
 *
 * <p>fallback 을 두지 않는다 — 예외를 그대로 전파해 호출자({@link PgSimulatorGateway})가
 * 예외 종류(500 vs 타임아웃 vs CB open)에 따라 재시도/미확정을 판단하도록 한다.
 */
@RequiredArgsConstructor
@Component
public class PgCircuitClient {

    private final PgSimulatorClient pgSimulatorClient;

    /**
     * 결제 요청 1회. 이 메서드 1회 호출 = 서킷브레이커 1 call.
     *
     * @throws feign.FeignException.InternalServerError PG 요청 거부(HTTP 500)
     * @throws feign.RetryableException 타임아웃·연결 오류
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException 회로 open 상태
     */
    @CircuitBreaker(name = "pg-simulator")
    public PgPaymentDto.TransactionResponse requestOnce(String userId, PgPaymentDto.PaymentRequest request) {
        return pgSimulatorClient.request(userId, request).data();
    }
}
