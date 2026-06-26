package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentMethod;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpPaymentGatewayTest {

    private RestTemplate restTemplate;
    private CircuitBreaker circuitBreaker;
    private HttpPaymentGateway httpPaymentGateway;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        
        // 테스트용 서킷 브레이커 설정
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .build();
        
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        circuitBreaker = registry.circuitBreaker("pgCircuitBreaker");
        
        httpPaymentGateway = new HttpPaymentGateway(restTemplate, circuitBreaker);
    }

    @Test
    @DisplayName("결제 요청 실패율이 50%를 초과하면 서킷 브레이커가 OPEN 상태로 전환된다")
    void circuitBreaker_Opens_On_FailureRate_Exceeding_50() {
        // given
        when(restTemplate.postForEntity(any(String.class), any(HttpEntity.class), any(Class.class)))
                .thenThrow(new RuntimeException("PG Connection Timeout"));

        // 10번 호출 시도 (슬라이딩 윈도우 크기 = 10)
        for (int i = 0; i < 10; i++) {
            try {
                httpPaymentGateway.requestPayment(1L, BigDecimal.valueOf(1000), PaymentMethod.CARD);
            } catch (Exception ignored) {
            }
        }

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("서킷 브레이커가 OPEN 상태일 때 결제를 요청하면 CallNotPermittedException이 발생한다")
    void shouldThrowCallNotPermittedException_WhenCircuitBreakerIsOpen() {
        // given
        circuitBreaker.transitionToOpenState();

        // when & then
        assertThatThrownBy(() -> httpPaymentGateway.requestPayment(1L, BigDecimal.valueOf(1000), PaymentMethod.CARD))
                .isInstanceOf(CallNotPermittedException.class);
    }
}
