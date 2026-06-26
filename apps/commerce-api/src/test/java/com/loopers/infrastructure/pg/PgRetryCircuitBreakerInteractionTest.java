package com.loopers.infrastructure.pg;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import feign.Feign;
import feign.RetryableException;
import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운영 aspect-order 재현:
 *   circuit-breaker-aspect-order=1 (외부) → retry-aspect-order=2 (내부)
 *
 * CB는 재시도 시퀀스 결과만 카운팅한다. 각 시퀀스 내부에서 Retry가 몇 번
 * HTTP 요청을 보냈는지는 CB의 failure/success count에 영향을 주지 않는다.
 */
@DisplayName("PG Retry + CircuitBreaker 연동 테스트")
class PgRetryCircuitBreakerInteractionTest {

    private static final WireMockServer wireMockServer =
        new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private static final String PAYMENT_OK_JSON =
        "{\"data\":{\"transactionKey\":\"20260623:TR:abc123\",\"status\":\"PENDING\",\"reason\":null}}";

    private PgFeignClient pgFeignClient;
    private CircuitBreaker circuitBreaker;
    private Retry retry;

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        PgFeignClientConfig config = new PgFeignClientConfig();

        ObjectFactory<HttpMessageConverters> messageConverters =
            () -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter());

        pgFeignClient = Feign.builder()
            .contract(new SpringMvcContract())
            .encoder(new SpringEncoder(messageConverters))
            .decoder(config.decoder())
            .retryer(Retryer.NEVER_RETRY)
            .errorDecoder(config.errorDecoder())
            .target(PgFeignClient.class, wireMockServer.baseUrl());

        circuitBreaker = CircuitBreaker.of("pg-interaction", CircuitBreakerConfig.custom()
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .recordExceptions(RetryableException.class, IOException.class)
            .build());

        retry = Retry.of("pg-interaction-retry", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ZERO)
            .retryExceptions(RetryableException.class, IOException.class)
            .ignoreExceptions(
                feign.FeignException.BadRequest.class,
                feign.FeignException.Conflict.class)
            .build());
    }

    // CB(외부) → Retry(내부): circuit-breaker-aspect-order=1, retry-aspect-order=2
    private PgApiResponse.Payment requestWithCbAndRetry(PgPaymentRequest request) {
        return CircuitBreaker.decorateSupplier(circuitBreaker,
            Retry.decorateSupplier(retry, () -> pgFeignClient.requestPayment("1", request))
        ).get();
    }

    @DisplayName("3회 재시도가 모두 실패해도 CB는 시퀀스 단위로 1회 실패를 기록한다.")
    @Test
    void cb_records_one_failure_per_retry_sequence() {
        // Arrange
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(aResponse().withStatus(500)));

        // Act - 4 sequences × 3 retries = 12 HTTP calls, CB는 4 failures 기록
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> requestWithCbAndRetry(sampleRequest()))
                .isInstanceOf(RetryableException.class);
        }

        // Assert
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        wireMockServer.verify(12, postRequestedFor(urlEqualTo("/api/v1/payments")));
    }

    @DisplayName("CB가 OPEN 상태이면 Retry가 실행되지 않고 즉시 CallNotPermittedException이 발생한다.")
    @Test
    void retry_is_blocked_when_circuit_is_open() {
        // Arrange - CB를 OPEN 상태로 만든다 (4 sequences × 3 retries = 12 HTTP calls)
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> requestWithCbAndRetry(sampleRequest()))
                .isInstanceOf(RetryableException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        wireMockServer.resetAll();

        // Act - CB OPEN: Retry 없이 즉시 거부
        assertThatThrownBy(() -> requestWithCbAndRetry(sampleRequest()))
            .isInstanceOf(CallNotPermittedException.class);

        // Assert - resetAll 이후 추가 HTTP 요청 없음
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/api/v1/payments")));
    }

    @DisplayName("재시도 중 성공하면 CB는 최종 성공으로 카운팅하여 중간 실패를 마스킹한다.")
    @Test
    void cb_records_success_when_retry_eventually_succeeds() {
        // Arrange - 첫 번째 시도 500, 두 번째 시도 성공
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
            .inScenario("retry-partial")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("1st-fail"));
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
            .inScenario("retry-partial")
            .whenScenarioStateIs("1st-fail")
            .willReturn(okJson(PAYMENT_OK_JSON)));

        // Act - 4회 모두 2번째 시도에 성공 → CB는 4 successes 기록
        for (int i = 0; i < 4; i++) {
            PgApiResponse.Payment response = requestWithCbAndRetry(sampleRequest());
            assertThat(response.transactionKey()).isEqualTo("20260623:TR:abc123");
            wireMockServer.resetScenarios();
        }

        // Assert - 중간 실패가 마스킹되어 CB는 CLOSED 유지
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        // 각 시퀀스당 2번 HTTP 요청 (1 fail + 1 success) × 4 = 8
        wireMockServer.verify(8, postRequestedFor(urlEqualTo("/api/v1/payments")));
    }

    private PgPaymentRequest sampleRequest() {
        return new PgPaymentRequest(
            "100", "SAMSUNG", "1234-5678-9012-3456", 50000L,
            "http://localhost:8080/api/v1/payments/callback"
        );
    }
}