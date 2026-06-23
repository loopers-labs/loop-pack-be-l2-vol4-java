package com.loopers.infrastructure.pg;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import feign.Feign;
import feign.RetryableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
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

        // 실제 운영에서 사용하는 PgFeignClientConfig의 Retryer/ErrorDecoder를 그대로 사용
        pgFeignClient = Feign.builder()
            .contract(new SpringMvcContract())
            .encoder(new SpringEncoder(messageConverters))
            .decoder(new SpringDecoder(messageConverters))
            .retryer(config.retryer())
            .errorDecoder(config.errorDecoder())
            .target(PgFeignClient.class, wireMockServer.baseUrl());
    }

    @DisplayName("PG 서버가 500을 반환할 때")
    @Nested
    class When500Error {

        @DisplayName("3회 연속 500이면 RetryableException이 발생하고 총 3번 요청한다.")
        @Test
        void throws_afterMaxRetries_whenAllAttemptsFail() {
            // Arrange
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

            // Act & Assert
            assertThatThrownBy(() -> pgFeignClient.requestPayment("1", sampleRequest()))
                .isInstanceOf(RetryableException.class);

            wireMockServer.verify(3, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }

        @DisplayName("500이 2번 후 3번째에 성공하면 총 3번 요청 후 정상 응답을 반환한다.")
        @Test
        void returns_success_onThirdAttempt() {
            // Arrange
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .inScenario("retry-success")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("1st-fail"));

            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .inScenario("retry-success")
                .whenScenarioStateIs("1st-fail")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("2nd-fail"));

            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .inScenario("retry-success")
                .whenScenarioStateIs("2nd-fail")
                .willReturn(okJson("{\"transactionKey\":\"20260623:TR:abc123\",\"status\":\"PENDING\",\"reason\":null}")));

            // Act
            PgPaymentResponse response = pgFeignClient.requestPayment("1", sampleRequest());

            // Assert
            assertThat(response.transactionKey()).isEqualTo("20260623:TR:abc123");
            wireMockServer.verify(3, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }

        @DisplayName("500이 1번 후 2번째에 성공하면 총 2번 요청 후 정상 응답을 반환한다.")
        @Test
        void returns_success_onSecondAttempt() {
            // Arrange
            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .inScenario("early-retry-success")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("1st-fail"));

            wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .inScenario("early-retry-success")
                .whenScenarioStateIs("1st-fail")
                .willReturn(okJson("{\"transactionKey\":\"20260623:TR:def456\",\"status\":\"PENDING\",\"reason\":null}")));

            // Act
            PgPaymentResponse response = pgFeignClient.requestPayment("1", sampleRequest());

            // Assert
            assertThat(response.transactionKey()).isEqualTo("20260623:TR:def456");
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
