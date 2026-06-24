package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayCommand;
import com.loopers.domain.payment.PaymentGateway;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpServerErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resilience4j aspect order 검증 — CircuitBreaker가 Retry를 감싸므로,
 * 한 번의 결제 요청이 내부에서 여러 번 재시도되어도 서킷에는 최종 결과 1건만 집계되어야 한다.
 * (기본 순서였다면 재시도 횟수만큼 집계되어 윈도우가 차고 OPEN으로 떨어진다.)
 */
@SpringBootTest
@TestPropertySource(properties = {
    "resilience4j.circuitbreaker.instances.pgCircuit.sliding-window-type=COUNT_BASED",
    "resilience4j.circuitbreaker.instances.pgCircuit.sliding-window-size=2",
    "resilience4j.circuitbreaker.instances.pgCircuit.minimum-number-of-calls=2",
    "resilience4j.circuitbreaker.instances.pgCircuit.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.pgCircuit.wait-duration-in-open-state=60s",
    "resilience4j.retry.instances.pgRequestRetry.max-attempts=2",
    "resilience4j.retry.instances.pgRequestRetry.wait-duration=1ms"
})
class PgGatewayAspectOrderTest {

    @MockitoBean
    private PgClient pgClient;
    @Autowired
    private PaymentGateway paymentGateway;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final GatewayCommand CMD =
        new GatewayCommand(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 5_000L);

    @BeforeEach
    void resetCircuit() {
        circuitBreakerRegistry.circuitBreaker("pgCircuit").reset();
    }

    @DisplayName("한 번의 결제 요청이 여러 번 재시도될 시 서킷은 호출 1건만 집계하고 닫힌 상태를 유지한다")
    @Test
    void circuitRecordsOnce_whenSingleRequestRetried() {
        // 5xx는 거래 생성 前 거절이라 pgRequestRetry가 재시도하는 유일한 케이스
        when(pgClient.requestPayment(any())).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        paymentGateway.requestPayment(CMD); // 재시도 소진 후 Fallback → pending (예외 전파 X)

        CircuitBreaker circuit = circuitBreakerRegistry.circuitBreaker("pgCircuit");
        assertThat(circuit.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(pgClient, times(2)).requestPayment(any());
    }
}
