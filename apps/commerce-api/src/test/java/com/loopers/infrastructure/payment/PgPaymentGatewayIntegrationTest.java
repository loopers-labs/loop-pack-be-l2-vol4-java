package com.loopers.infrastructure.payment;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PgPaymentGatewayIntegrationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().dynamicPort())
        .build();

    @DynamicPropertySource
    static void overridePgBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("pg.base-url", wiremock::baseUrl);
    }

    @Autowired
    private PaymentGateway paymentGateway;

    private static final PaymentGateway.Command COMMAND = new PaymentGateway.Command(
        1L, 1351039135L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L
    );

    private static final String SUCCESS_BODY =
        "{\"meta\":{\"result\":\"SUCCESS\"},\"data\":{\"transactionKey\":\"20260626:TR:abc123\",\"status\":\"PENDING\",\"reason\":null}}";

    @DisplayName("PG가 정상 응답하면, transactionKey와 PENDING 상태를 반환한다.")
    @Test
    void returnsResult_whenPgRespondsOk() {
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments")).willReturn(okJson(SUCCESS_BODY)));

        PaymentGateway.Result result = paymentGateway.requestPayment(COMMAND);

        assertAll(
            () -> assertThat(result.transactionKey()).isEqualTo("20260626:TR:abc123"),
            () -> assertThat(result.status()).isEqualTo(PaymentStatus.PENDING)
        );
    }

    @DisplayName("PG가 500을 반환하면, CoreException(INTERNAL_ERROR)으로 변환된다.")
    @Test
    void throwsCoreException_whenPgReturns500() {
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments")).willReturn(aResponse().withStatus(500)));

        CoreException ex = assertThrows(CoreException.class, () -> paymentGateway.requestPayment(COMMAND));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
    }

    @DisplayName("PG 응답이 read-timeout(2s)을 초과하면, CoreException으로 변환된다.")
    @Test
    void throwsCoreException_whenTimeout() {
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments"))
            .willReturn(okJson(SUCCESS_BODY).withFixedDelay(4000)));

        CoreException ex = assertThrows(CoreException.class, () -> paymentGateway.requestPayment(COMMAND));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
    }
}
