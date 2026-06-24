package com.loopers.infrastructure.pg;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import feign.Feign;
import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

@DisplayName("PG FeignClient 카오스 테스트")
class PgFeignClientChaosTest {

    private static final WireMockServer wireMockServer =
        new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private PgFeignClient pgFeignClient;
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

        // 운영 설정과 동일한 구성 (단, 테스트에서는 대기 없이 즉시 재시도)
        retry = Retry.of("pg-payment", RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ZERO)
            .retryExceptions(RetryableException.class, IOException.class)
            .build());
    }

    private PgApiResponse.Payment requestPaymentWithRetry(PgPaymentRequest request) {
        Supplier<PgApiResponse.Payment> supplier = Retry.decorateSupplier(retry,
            () -> pgFeignClient.requestPayment("1", request));
        return supplier.get();
    }

    @DisplayName("PG 서버가 500을 반환할 때")
    @Nested
    class When500Error {

        @DisplayName("2회 연속 500이면 RetryableException이 발생하고 총 2번 요청한다.")
        @Test
        void throws_afterMaxRetries_whenAllAttemptsFail() {
            // Arrange
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

            // Act & Assert
            assertThatThrownBy(() -> requestPaymentWithRetry(sampleRequest()))
                .isInstanceOf(RetryableException.class);

            wireMockServer.verify(2, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }

        @DisplayName("500이 1번 후 2번째에 성공하면 총 2번 요청 후 정상 응답을 반환한다.")
        @Test
        void returns_success_onSecondAttempt() {
            // Arrange
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .inScenario("retry-success")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("1st-fail"));

            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .inScenario("retry-success")
                .whenScenarioStateIs("1st-fail")
                .willReturn(okJson("{\"data\":{\"transactionKey\":\"20260623:TR:abc123\",\"status\":\"PENDING\",\"reason\":null}}")));

            // Act
            PgApiResponse.Payment response = requestPaymentWithRetry(sampleRequest());

            // Assert
            assertThat(response.transactionKey()).isEqualTo("20260623:TR:abc123");
            wireMockServer.verify(2, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }
    }

    @DisplayName("PG 서버가 첫 번째 시도에 성공할 때")
    @Nested
    class WhenSuccess {

        @DisplayName("1번만 요청하고 정상 응답을 반환한다.")
        @Test
        void returns_success_onFirstAttempt() {
            // Arrange
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(okJson("{\"data\":{\"transactionKey\":\"20260623:TR:abc123\",\"status\":\"PENDING\",\"reason\":null}}")));

            // Act
            PgApiResponse.Payment response = requestPaymentWithRetry(sampleRequest());

            // Assert
            assertThat(response.transactionKey()).isEqualTo("20260623:TR:abc123");
            wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }
    }

    @DisplayName("PG 서버와 네트워크 연결이 끊어질 때")
    @Nested
    class WhenNetworkFault {

        @DisplayName("2회 연속 연결 단절이면 RetryableException이 발생하고 총 2번 요청한다.")
        @Test
        void throws_afterMaxRetries_whenConnectionReset() {
            // Arrange
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            // Act & Assert
            assertThatThrownBy(() -> requestPaymentWithRetry(sampleRequest()))
                .isInstanceOf(RetryableException.class);

            wireMockServer.verify(2, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }
    }

    @DisplayName("PG 서버가 응답하지 않을 때 (타임아웃)")
    @Nested
    class WhenTimeout {

        private PgFeignClient pgFeignClientWithShortTimeout;
        private Retry retryForTimeout;

        @BeforeEach
        void setUpWithShortTimeout() {
            PgFeignClientConfig config = new PgFeignClientConfig();
            ObjectFactory<HttpMessageConverters> messageConverters =
                () -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter());

            // 테스트 속도를 위해 read timeout을 200ms로 단축
            pgFeignClientWithShortTimeout = Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(messageConverters))
                .decoder(config.decoder())
                .retryer(Retryer.NEVER_RETRY)
                .errorDecoder(config.errorDecoder())
                .options(new Request.Options(200, TimeUnit.MILLISECONDS, 200, TimeUnit.MILLISECONDS, true))
                .target(PgFeignClient.class, wireMockServer.baseUrl());

            retryForTimeout = Retry.of("pg-payment-timeout", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ZERO)
                .retryExceptions(RetryableException.class, IOException.class)
                .build());
        }

        @DisplayName("2회 모두 타임아웃이면 RetryableException이 발생하고 총 2번 요청한다.")
        @Test
        void throws_afterMaxRetries_whenAllAttemptsTimeout() {
            // Arrange
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withFixedDelay(500)));

            Supplier<PgApiResponse.Payment> supplier = Retry.decorateSupplier(retryForTimeout,
                () -> pgFeignClientWithShortTimeout.requestPayment("1", sampleRequest()));

            // Act & Assert
            assertThatThrownBy(supplier::get)
                .isInstanceOf(RetryableException.class);

            wireMockServer.verify(2, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }
    }

    private PgPaymentRequest sampleRequest() {
        return new PgPaymentRequest(
            "100", "SAMSUNG", "1234-5678-9012-3456", 50000L,
            "http://localhost:8080/api/v1/payments/callback"
        );
    }
}
