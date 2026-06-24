package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PgPaymentGatewayTest {

    private static final String BASE_URL = "http://localhost:8082";
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    private MockRestServiceServer server;
    private PgPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        PgClientProperties properties =
            new PgClientProperties(BASE_URL, CALLBACK_URL, Duration.ofSeconds(1), Duration.ofSeconds(1));
        gateway = new PgPaymentGateway(restTemplate, properties);
    }

    @DisplayName("결제를 접수 요청할 때, ")
    @Nested
    class RequestPayment {

        @DisplayName("PG 로 X-USER-ID 헤더와 zero-pad 된 orderId·callbackUrl 을 담아 POST 한다.")
        @Test
        void sendsRequest_withUserIdHeaderAndEncodedBody() {
            // given
            server.expect(requestTo(BASE_URL + "/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-USER-ID", "1"))
                .andExpect(jsonPath("$.orderId").value("000100"))
                .andExpect(jsonPath("$.cardType").value("SAMSUNG"))
                .andExpect(jsonPath("$.cardNo").value("1234-5678-9814-1451"))
                .andExpect(jsonPath("$.callbackUrl").value(CALLBACK_URL))
                .andRespond(withSuccess(pendingResponseJson(), MediaType.APPLICATION_JSON));

            PaymentGatewayCommand command =
                new PaymentGatewayCommand(1L, 100L, "SAMSUNG", "1234-5678-9814-1451", 50_000L);

            // when
            gateway.requestPayment(command);

            // then
            server.verify();
        }

        @DisplayName("PG 가 PENDING + transactionKey 를 접수증으로 응답하면, 그 값을 결과로 반환한다.")
        @Test
        void returnsTransactionKeyAndPending_whenPgAcknowledges() {
            // given
            server.expect(requestTo(BASE_URL + "/api/v1/payments"))
                .andRespond(withSuccess(pendingResponseJson(), MediaType.APPLICATION_JSON));

            PaymentGatewayCommand command =
                new PaymentGatewayCommand(1L, 100L, "SAMSUNG", "1234-5678-9814-1451", 50_000L);

            // when
            PaymentGatewayResult result = gateway.requestPayment(command);

            // then
            assertThat(result.transactionKey()).isEqualTo("20260624:TR:abc123");
            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    private String pendingResponseJson() {
        return """
            {
              "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
              "data": { "transactionKey": "20260624:TR:abc123", "status": "PENDING", "reason": null }
            }
            """;
    }
}
