package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.support.error.CoreException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CircuitBreaker 집계 정책 검증(tasks T6.1): paymentRequest CB 가 PaymentGatewayException 은 실패로 집계하고
 * CoreException(4xx) 은 무시하는지(설계 §7.2 record-/ignore-exceptions)를 실제 어댑터 경로로 확인한다.
 */
@SpringBootTest
class PaymentGatewayCircuitBreakerIntegrationTest {

    private static final PgPaymentCommand COMMAND =
            new PgPaymentCommand("20260626000000000001", CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        ThrowingStubFeignClient throwingStubFeignClient() {
            return new ThrowingStubFeignClient();
        }
    }

    @Autowired
    private PaymentGateway paymentGateway;
    @Autowired
    private ThrowingStubFeignClient stub;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        circuitBreakerRegistry.circuitBreaker("paymentRequest").reset();
    }

    @AfterEach
    void tearDown() {
        circuitBreakerRegistry.circuitBreaker("paymentRequest").reset();
    }

    @DisplayName("미도달(5xx → PaymentGatewayException)은 CB 가 실패로 집계한다.")
    @Test
    void recordsFailure_whenGatewayException() {
        // given
        stub.toThrow = () -> feignException(500);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentRequest");

        // when
        assertThrows(PaymentGatewayException.class, () -> paymentGateway.request(COMMAND));

        // then
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isGreaterThan(0);
    }

    @DisplayName("4xx(CoreException)은 CB 가 무시한다(실패로 집계하지 않음).")
    @Test
    void ignoresFailure_whenCoreException() {
        // given
        stub.toThrow = () -> feignException(400);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentRequest");

        // when
        assertThrows(CoreException.class, () -> paymentGateway.request(COMMAND));

        // then
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
    }

    // ---- helpers ----

    private static Request dummyRequest() {
        return Request.create(Request.HttpMethod.POST, "http://localhost:8082/api/v1/payments",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    }

    private static FeignException feignException(int status) {
        Response response = Response.builder()
                .status(status)
                .reason("error")
                .request(dummyRequest())
                .headers(Map.of())
                .build();
        return FeignException.errorStatus("PaymentGatewayFeignClient#request", response);
    }

    static class ThrowingStubFeignClient implements PaymentGatewayFeignClient {
        java.util.function.Supplier<RuntimeException> toThrow;

        @Override
        public PgApiResponse<PgTransactionResponse> request(String userId, PgPaymentRequest body) {
            throw toThrow.get();
        }

        @Override
        public PgApiResponse<PgTransactionDetailResponse> getByKey(String userId, String transactionKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PgApiResponse<PgOrderResponse> getByOrderId(String userId, String orderId) {
            throw new UnsupportedOperationException();
        }
    }
}
