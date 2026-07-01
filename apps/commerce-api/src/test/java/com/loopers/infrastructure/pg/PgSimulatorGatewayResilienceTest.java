package com.loopers.infrastructure.pg;

import com.loopers.domain.pg.PgGateway;
import com.loopers.domain.pg.PgTransactionResult;
import com.loopers.domain.pg.PgTransactionStatus;
import com.loopers.support.error.CoreException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class PgSimulatorGatewayResilienceTest {

    // 실제 PgSimulatorGateway 빈 — @Retry, @CircuitBreaker AOP 프록시로 감싸진 상태
    @Autowired
    private PgGateway pgGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    // AopTestUtils로 실제 PgSimulatorGateway 추출 → ReflectionTestUtils로 RestTemplate 교체
    private RestTemplate pgRestTemplate;

    @BeforeEach
    void setUp() {
        pgRestTemplate = mock(RestTemplate.class);

        // AOP 프록시(Retry → CB) 안에 실제 PgSimulatorGateway 객체 추출
        PgSimulatorGateway target = AopTestUtils.getUltimateTargetObject(pgGateway);

        // PgSimulatorGateway.pgRestTemplate 필드에 mock 주입
        ReflectionTestUtils.setField(target, "pgRestTemplate", pgRestTemplate);

        // 테스트 간 CB 상태 및 메트릭 초기화
        circuitBreakerRegistry.circuitBreaker("pgCircuit").reset();
    }

    private PgTransactionResult request() {
        return pgGateway.request("user-1", "order-1", "SAMSUNG", "1234-5678-9012-3456", 10000L, "http://callback");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pgResponse(String transactionKey) {
        Map<String, Object> data = new HashMap<>();
        data.put("transactionKey", transactionKey);
        data.put("status", "PENDING");
        data.put("reason", null);
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        return response;
    }

    // postForObject(String, Object, Class, Object...) — String 오버로드 명시
    // any()만 쓰면 Java overload resolution이 postForObject(URI, Object, Class)를 선택해 stub/verify 불일치
    // anyString()으로 String+varargs 오버로드로 강제 지정
    private void givenPostForObjectThrows(Throwable t) {
        given(pgRestTemplate.postForObject(anyString(), any(), any()))
            .willThrow(t);
    }

    @SuppressWarnings("unchecked")
    private void givenPostForObjectReturns(Map<String, Object> response) {
        given(pgRestTemplate.postForObject(anyString(), any(), any()))
            .willReturn(response);
    }

    private void verifyPostForObjectCalledTimes(int times) {
        verify(pgRestTemplate, times(times)).postForObject(anyString(), any(), any());
    }

    private void verifyPostForObjectNeverCalled() {
        verify(pgRestTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Nested
    @DisplayName("Retry")
    class RetryTest {

        @Test
        @DisplayName("5xx 응답 시 max-attempts(3)만큼 시도 후 fallback")
        void retries_3times_on_5xx() {
            givenPostForObjectThrows(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThrows(CoreException.class, () -> request());

            // PgSimulatorGateway.request()가 Retry에 의해 정확히 3번 호출됐는지 검증
            verifyPostForObjectCalledTimes(3);
        }

        @Test
        @DisplayName("4xx 응답 시 재시도 없이 1번만 호출")
        void noRetry_on_4xx() {
            // 4xx → PgSimulatorGateway catch 블록 → CoreException 변환
            // CoreException은 retry-exceptions에 없음 → 재시도 없이 fallback
            givenPostForObjectThrows(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

            assertThrows(CoreException.class, () -> request());

            verifyPostForObjectCalledTimes(1);
        }

        @Test
        @DisplayName("1번 실패 후 재시도 성공 시 결과 반환")
        void succeeds_on_second_attempt() {
            String expectedKey = "TR:abc123";

            // 첫 번째 호출: 500 → 두 번째 호출: 성공 응답
            given(pgRestTemplate.postForObject(anyString(), any(), any()))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .willReturn(pgResponse(expectedKey));

            PgTransactionResult result = request();

            assertAll(
                () -> assertThat(result.transactionKey()).isEqualTo(expectedKey),
                () -> assertThat(result.status()).isEqualTo(PgTransactionStatus.PENDING)
            );
            verifyPostForObjectCalledTimes(2);
        }
    }

    @Nested
    @DisplayName("Circuit Breaker")
    class CircuitBreakerTest {

        @Test
        @DisplayName("minimum-number-of-calls(5) 미만이면 CB CLOSED 유지")
        void staysClosed_belowMinimumCalls() {
            // 1번 request() = retry 3번 = CB 3건 기록 → minimum 5 미달 → CLOSED 유지
            givenPostForObjectThrows(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThrows(CoreException.class, () -> request());

            assertThat(circuitBreakerRegistry.circuitBreaker("pgCircuit").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("minimum(5) 초과 + 실패율 50% 이상이면 OPEN")
        void opens_afterFailureThreshold() {
            // 2번 request() = retry 6건 → minimum 5 초과, 실패율 100% → OPEN
            givenPostForObjectThrows(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThrows(CoreException.class, () -> request());
            assertThrows(CoreException.class, () -> request());

            assertThat(circuitBreakerRegistry.circuitBreaker("pgCircuit").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("CB OPEN 시 PG HTTP 호출 없이 즉시 fallback")
        void noHttpCall_whenCircuitOpen() {
            // CB 강제 OPEN
            circuitBreakerRegistry.circuitBreaker("pgCircuit").transitionToOpenState();

            // CB OPEN → CallNotPermittedException → Retry ignore-exceptions → 즉시 fallback → CoreException
            assertThrows(CoreException.class, () -> request());

            // postForObject 한 번도 호출 안 됨
            verifyPostForObjectNeverCalled();
        }

        @Test
        @DisplayName("CB OPEN 시 CallNotPermittedException ignore → 재시도 없이 CB 거절 1회만")
        void callNotPermitted_onlyOnce_whenCircuitOpen() {
            // CB 강제 OPEN
            circuitBreakerRegistry.circuitBreaker("pgCircuit").transitionToOpenState();

            assertThrows(CoreException.class, () -> request());

            // 재시도했다면 3, ignore-exceptions 적용으로 재시도 없으면 1
            long notPermitted = circuitBreakerRegistry.circuitBreaker("pgCircuit")
                .getMetrics().getNumberOfNotPermittedCalls();
            assertThat(notPermitted).isEqualTo(1L);
        }
    }
}
