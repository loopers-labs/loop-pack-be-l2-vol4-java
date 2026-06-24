package com.loopers.infrastructure.pg;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import feign.Feign;
import feign.RetryableException;
import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;
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

@DisplayName("PG CircuitBreaker 테스트")
class PgCircuitBreakerTest {

    private static final WireMockServer wireMockServer =
        new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private PgFeignClient pgFeignClient;
    private CircuitBreaker circuitBreaker;

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

        // 운영 설정과 동일한 구성 (단, 테스트용으로 window를 4로 축소하고 OPEN 유지)
        circuitBreaker = CircuitBreaker.of("pg-payment", CircuitBreakerConfig.custom()
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .recordExceptions(RetryableException.class, IOException.class)
            .build());
    }

    private PgApiResponse.Payment requestPayment(PgPaymentRequest request) {
        Supplier<PgApiResponse.Payment> supplier = CircuitBreaker.decorateSupplier(
            circuitBreaker, () -> pgFeignClient.requestPayment("1", request));
        return supplier.get();
    }

    @DisplayName("최소 호출 횟수에 도달하지 않으면 실패율이 100%여도 CB는 열리지 않는다.")
    @Test
    void stays_closed_below_minimum_number_of_calls() {
        // Arrange
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(aResponse().withStatus(500)));

        // Act - minimum 4회보다 적은 3회 실패
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> requestPayment(sampleRequest()))
                .isInstanceOf(RetryableException.class);
        }

        // Assert
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/api/v1/payments")));
    }

    @DisplayName("실패율이 임계값을 초과하면 CB가 열리고 이후 호출은 즉시 거부된다.")
    @Test
    void opens_and_rejects_calls_when_failure_rate_exceeded() {
        // Arrange
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(aResponse().withStatus(500)));

        // Act - 4회 실패 → 실패율 100% > 50% → CB OPEN
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> requestPayment(sampleRequest()))
                .isInstanceOf(RetryableException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 5번째 호출: CB가 즉시 거부 (WireMock에 요청 도달하지 않음)
        assertThatThrownBy(() -> requestPayment(sampleRequest()))
            .isInstanceOf(CallNotPermittedException.class);

        // Assert - WireMock은 4번만 요청을 수신했어야 함
        wireMockServer.verify(4, postRequestedFor(urlEqualTo("/api/v1/payments")));
    }

    @DisplayName("정상 호출만 있으면 CB는 CLOSED 상태를 유지한다.")
    @Test
    void stays_closed_on_successful_calls() {
        // Arrange
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(okJson("{\"data\":{\"transactionKey\":\"20260623:TR:abc123\",\"status\":\"PENDING\",\"reason\":null}}")));

        // Act
        for (int i = 0; i < 4; i++) {
            PgApiResponse.Payment response = requestPayment(sampleRequest());
            assertThat(response.transactionKey()).isEqualTo("20260623:TR:abc123");
        }

        // Assert
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private PgPaymentRequest sampleRequest() {
        return new PgPaymentRequest(
            "100", "SAMSUNG", "1234-5678-9012-3456", 50000L,
            "http://localhost:8080/api/v1/payments/callback"
        );
    }
}
