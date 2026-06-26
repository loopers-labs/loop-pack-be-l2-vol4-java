package com.loopers.infrastructure.payment;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.support.error.CoreException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "pg.base-url=http://localhost:18085")
class PgPaymentGatewayResilienceTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().port(18085))
        .build();

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final PaymentGateway.Command CMD =
        new PaymentGateway.Command(1L, 1351039135L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
    private static final String OK_BODY =
        "{\"meta\":{\"result\":\"SUCCESS\"},\"data\":{\"transactionKey\":\"20260626:TR:abc123\",\"status\":\"PENDING\",\"reason\":null}}";

    @BeforeEach
    void reset() {
        wiremock.resetAll();
        circuitBreakerRegistry.circuitBreaker("pgCircuit").reset();
    }

    @DisplayName("요청단계 5xx는 재시도하여, 일시 실패 후 성공하면 정상 결과를 받는다.")
    @Test
    void retriesOn5xx_thenSucceeds() {
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments")).inScenario("retry")
            .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
            .willReturn(aResponse().withStatus(500)).willSetStateTo("2"));
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments")).inScenario("retry")
            .whenScenarioStateIs("2")
            .willReturn(aResponse().withStatus(500)).willSetStateTo("3"));
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments")).inScenario("retry")
            .whenScenarioStateIs("3")
            .willReturn(okJson(OK_BODY)));

        PaymentGateway.Result result = paymentGateway.requestPayment(CMD);

        assertThat(result.transactionKey()).isEqualTo("20260626:TR:abc123");
        wiremock.verify(3, postRequestedFor(urlEqualTo("/api/v1/payments")));
    }

    @DisplayName("타임아웃은 재시도하지 않는다 (이중 결제 방지) — 요청은 1회만 나간다.")
    @Test
    void doesNotRetryOnTimeout() {
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(okJson(OK_BODY).withFixedDelay(4000)));

        assertThrows(CoreException.class, () -> paymentGateway.requestPayment(CMD));

        wiremock.verify(1, postRequestedFor(urlEqualTo("/api/v1/payments")));
    }

    @DisplayName("반복 실패로 서킷이 열리면, 이후 호출은 PG에 닿지 않고 빠르게 실패한다.")
    @Test
    void circuitOpens_afterRepeatedFailures() {
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments")).willReturn(aResponse().withStatus(500)));

        // 2회 호출 × 재시도 3회 = 6 실패 → 윈도우(6) 가득 → 서킷 OPEN
        for (int i = 0; i < 2; i++) {
            assertThrows(CoreException.class, () -> paymentGateway.requestPayment(CMD));
        }
        // 서킷 OPEN 상태 — 다음 호출은 PG에 닿지 않아야 함
        assertThrows(CoreException.class, () -> paymentGateway.requestPayment(CMD));

        wiremock.verify(6, postRequestedFor(urlEqualTo("/api/v1/payments")));
    }
}
