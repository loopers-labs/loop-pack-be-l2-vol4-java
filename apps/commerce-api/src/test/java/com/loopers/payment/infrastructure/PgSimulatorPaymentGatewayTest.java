package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentGatewayPaymentCommand;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentGatewayRequestStatus;
import com.loopers.payment.domain.PgPaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PgSimulatorPaymentGatewayTest {

    private static final String BASE_URL = "http://localhost:8082";
    private static final PaymentGatewayPaymentCommand COMMAND = new PaymentGatewayPaymentCommand(
        1L,
        1_351_039_135L,
        CardType.SAMSUNG,
        "1234-5678-9814-1451",
        5_000L
    );

    @DisplayName("PG 결제 요청이 성공하면, PG 거래 정보를 반환한다.")
    @Test
    void returnsTransaction_whenPgRequestSucceeds() {
        // arrange
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PgSimulatorPaymentGateway gateway = new PgSimulatorPaymentGateway(properties(), restTemplate);

        server.expect(once(), requestTo(BASE_URL + "/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-USER-ID", "1"))
            .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.orderId").value("1351039135"))
            .andExpect(jsonPath("$.cardType").value("SAMSUNG"))
            .andExpect(jsonPath("$.cardNo").value("1234-5678-9814-1451"))
            .andExpect(jsonPath("$.amount").value(5_000))
            .andExpect(jsonPath("$.callbackUrl").value("http://localhost:8080/api/v1/payments/callback"))
            .andRespond(withSuccess("""
                {
                  "meta": {
                    "result": "SUCCESS",
                    "errorCode": null,
                    "message": null
                  },
                  "data": {
                    "transactionKey": "20250816:TR:9577c5",
                    "status": "PENDING",
                    "reason": null
                  }
                }
                """, MediaType.APPLICATION_JSON));

        // act
        PaymentGatewayResult result = gateway.requestPayment(COMMAND);

        // assert
        assertAll(
            () -> assertThat(result.isRequestSucceeded()).isTrue(),
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayRequestStatus.SUCCEEDED),
            () -> assertThat(result.transaction().transactionKey()).isEqualTo("20250816:TR:9577c5"),
            () -> assertThat(result.transaction().status()).isEqualTo(PgPaymentStatus.PENDING),
            () -> assertThat(result.failureReason()).isNull()
        );
        server.verify();
    }

    @DisplayName("PG 결제 요청이 5xx로 실패하면, 요청 실패 결과를 반환한다.")
    @Test
    void returnsRequestFailed_whenPgReturnsServerError() {
        // arrange
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PgSimulatorPaymentGateway gateway = new PgSimulatorPaymentGateway(properties(), restTemplate);

        server.expect(once(), requestTo(BASE_URL + "/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "meta": {
                        "result": "FAIL",
                        "errorCode": "Internal Server Error",
                        "message": "현재 서버가 불안정합니다. 잠시 후 다시 시도해주세요."
                      },
                      "data": null
                    }
                    """));

        // act
        PaymentGatewayResult result = gateway.requestPayment(COMMAND);

        // assert
        assertAll(
            () -> assertThat(result.isRequestSucceeded()).isFalse(),
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayRequestStatus.FAILED),
            () -> assertThat(result.transaction()).isNull(),
            () -> assertThat(result.failureReason()).isEqualTo(PaymentFailureReason.PG_REQUEST_FAILED)
        );
        server.verify();
    }

    @DisplayName("PG 결제 요청이 timeout 되면, timeout 실패 결과를 반환한다.")
    @Test
    void returnsTimeout_whenPgRequestTimesOut() {
        // arrange
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PgSimulatorPaymentGateway gateway = new PgSimulatorPaymentGateway(properties(), restTemplate);

        server.expect(once(), requestTo(BASE_URL + "/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withException(new SocketTimeoutException("read timed out")));

        // act
        PaymentGatewayResult result = gateway.requestPayment(COMMAND);

        // assert
        assertAll(
            () -> assertThat(result.isRequestSucceeded()).isFalse(),
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayRequestStatus.UNKNOWN),
            () -> assertThat(result.transaction()).isNull(),
            () -> assertThat(result.failureReason()).isEqualTo(PaymentFailureReason.PG_TIMEOUT)
        );
        server.verify();
    }

    private PgSimulatorProperties properties() {
        return new PgSimulatorProperties(BASE_URL, "http://localhost:8080/api/v1/payments/callback", 1_000L, 10_000L);
    }
}
