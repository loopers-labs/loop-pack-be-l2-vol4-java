package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentGatewayTimeoutException;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.support.error.CoreException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
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

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Retry(Nice-To-Have) 검증: 미도달(5xx)만 자동 재시도하고, Read Timeout·4xx 는 재시도하지 않는다(설계 §7.4).
 * 실제 프록시된 PaymentGatewayImpl(@Retry 적용)에 카운팅 stub FeignClient 를 주입해 시도 횟수를 관찰한다.
 */
@SpringBootTest
class PaymentGatewayRetryIntegrationTest {

    private static final PgPaymentCommand COMMAND =
            new PgPaymentCommand("20260626000000000001", CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        CountingStubFeignClient countingStubFeignClient() {
            return new CountingStubFeignClient();
        }
    }

    @Autowired
    private PaymentGateway paymentGateway;
    @Autowired
    private CountingStubFeignClient stub;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        stub.count.set(0);
        circuitBreakerRegistry.circuitBreaker("paymentRequest").reset();
    }

    @AfterEach
    void tearDown() {
        circuitBreakerRegistry.circuitBreaker("paymentRequest").reset();
    }

    @DisplayName("미도달(5xx)이면 최대 시도 횟수(3회)만큼 재시도한다.")
    @Test
    void retriesThreeTimes_whenServerError() {
        // given
        stub.toThrow = () -> feignException(500);

        // when
        assertThrows(PaymentGatewayException.class, () -> paymentGateway.request(COMMAND));

        // then
        assertThat(stub.count.get()).isEqualTo(3);
    }

    @DisplayName("Read Timeout 이면 재시도하지 않는다(1회 시도, 이중결제 위험 회피).")
    @Test
    void doesNotRetry_whenReadTimeout() {
        // given
        stub.toThrow = () -> new RetryableException(-1, "Read timed out", Request.HttpMethod.POST,
                new SocketTimeoutException("Read timed out"), (Long) null, dummyRequest());

        // when
        assertThrows(PaymentGatewayTimeoutException.class, () -> paymentGateway.request(COMMAND));

        // then
        assertThat(stub.count.get()).isEqualTo(1);
    }

    @DisplayName("4xx(우리 측 버그)이면 재시도하지 않는다(1회 시도).")
    @Test
    void doesNotRetry_whenClientError() {
        // given
        stub.toThrow = () -> feignException(400);

        // when
        assertThrows(CoreException.class, () -> paymentGateway.request(COMMAND));

        // then
        assertThat(stub.count.get()).isEqualTo(1);
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

    static class CountingStubFeignClient implements PaymentGatewayFeignClient {
        final AtomicInteger count = new AtomicInteger(0);
        java.util.function.Supplier<RuntimeException> toThrow;

        @Override
        public PgApiResponse<PgTransactionResponse> request(String userId, PgPaymentRequest body) {
            count.incrementAndGet();
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
