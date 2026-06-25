package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentGatewayOrderTransactions;
import com.loopers.payment.domain.PaymentGatewayPaymentCommand;
import com.loopers.payment.domain.PaymentGatewayQueryResult;
import com.loopers.payment.domain.PaymentGatewayQueryStatus;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentGatewayRequestStatus;
import com.loopers.payment.domain.PaymentGatewayTransactionDetail;
import com.loopers.payment.domain.PgPaymentStatus;
import org.junit.jupiter.api.BeforeEach;
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

    private MockRestServiceServer server;
    private PgSimulatorPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        gateway = new PgSimulatorPaymentGateway(properties(), restTemplate);
    }

    @DisplayName("PG 결제 요청이 접수되면, PG 거래 정보를 반환한다.")
    @Test
    void returnsTransaction_whenPgRequestIsAccepted() {
        // arrange
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
            () -> assertThat(result.isRequestAccepted()).isTrue(),
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayRequestStatus.ACCEPTED),
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
            () -> assertThat(result.isRequestAccepted()).isFalse(),
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
        server.expect(once(), requestTo(BASE_URL + "/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withException(new SocketTimeoutException("read timed out")));

        // act
        PaymentGatewayResult result = gateway.requestPayment(COMMAND);

        // assert
        assertAll(
            () -> assertThat(result.isRequestAccepted()).isFalse(),
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayRequestStatus.UNKNOWN),
            () -> assertThat(result.transaction()).isNull(),
            () -> assertThat(result.failureReason()).isEqualTo(PaymentFailureReason.PG_TIMEOUT)
        );
        server.verify();
    }

    @DisplayName("PG 거래 키로 결제 상태를 조회하면, 거래 상세 정보를 반환한다.")
    @Test
    void returnsTransactionDetail_whenTransactionKeyExists() {
        // arrange
        server.expect(once(), requestTo(BASE_URL + "/api/v1/payments/20250816:TR:9577c5"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "1"))
            .andRespond(withSuccess("""
                {
                  "meta": {
                    "result": "SUCCESS",
                    "errorCode": null,
                    "message": null
                  },
                  "data": {
                    "transactionKey": "20250816:TR:9577c5",
                    "orderId": "1351039135",
                    "cardType": "SAMSUNG",
                    "cardNo": "1234-5678-9814-1451",
                    "amount": 5000,
                    "status": "SUCCESS",
                    "reason": null
                  }
                }
                """, MediaType.APPLICATION_JSON));

        // act
        PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> result = gateway.getTransaction(1L, "20250816:TR:9577c5");

        // assert
        PaymentGatewayTransactionDetail transaction = result.data();
        assertAll(
            () -> assertThat(result.isFound()).isTrue(),
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayQueryStatus.FOUND),
            () -> assertThat(transaction.transactionKey()).isEqualTo("20250816:TR:9577c5"),
            () -> assertThat(transaction.orderId()).isEqualTo(1_351_039_135L),
            () -> assertThat(transaction.cardType()).isEqualTo(CardType.SAMSUNG),
            () -> assertThat(transaction.amount()).isEqualTo(5_000L),
            () -> assertThat(transaction.status()).isEqualTo(PgPaymentStatus.SUCCESS),
            () -> assertThat(transaction.reason()).isNull()
        );
        server.verify();
    }

    @DisplayName("PG 거래 키 조회 결과가 없으면, 조회 실패 결과를 반환한다.")
    @Test
    void returnsNotFound_whenTransactionDoesNotExist() {
        // arrange
        server.expect(once(), requestTo(BASE_URL + "/api/v1/payments/20250816:TR:unknown"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "1"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "meta": {
                        "result": "FAIL",
                        "errorCode": "NOT_FOUND",
                        "message": "결제건이 존재하지 않습니다."
                      },
                      "data": null
                    }
                    """));

        // act
        PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> result = gateway.getTransaction(1L, "20250816:TR:unknown");

        // assert
        assertAll(
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayQueryStatus.NOT_FOUND),
            () -> assertThat(result.data()).isNull(),
            () -> assertThat(result.failureReason()).isEqualTo(PaymentFailureReason.PG_TRANSACTION_NOT_FOUND)
        );
        server.verify();
    }

    @DisplayName("PG 거래 키 조회가 5xx로 실패하면, 조회 실패 결과를 반환한다.")
    @Test
    void returnsFailed_whenTransactionQueryReturnsServerError() {
        // arrange
        server.expect(once(), requestTo(BASE_URL + "/api/v1/payments/20250816:TR:9577c5"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "1"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "meta": {
                        "result": "FAIL",
                        "errorCode": "Internal Server Error",
                        "message": "현재 서버가 불안정합니다."
                      },
                      "data": null
                    }
                    """));

        // act
        PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> result = gateway.getTransaction(1L, "20250816:TR:9577c5");

        // assert
        assertAll(
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayQueryStatus.FAILED),
            () -> assertThat(result.data()).isNull(),
            () -> assertThat(result.failureReason()).isEqualTo(PaymentFailureReason.PG_REQUEST_FAILED)
        );
        server.verify();
    }

    @DisplayName("PG 거래 키 조회가 timeout 되면, 확인 필요 결과를 반환한다.")
    @Test
    void returnsUnknown_whenTransactionQueryTimesOut() {
        // arrange
        server.expect(once(), requestTo(BASE_URL + "/api/v1/payments/20250816:TR:9577c5"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "1"))
            .andRespond(withException(new SocketTimeoutException("read timed out")));

        // act
        PaymentGatewayQueryResult<PaymentGatewayTransactionDetail> result = gateway.getTransaction(1L, "20250816:TR:9577c5");

        // assert
        assertAll(
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayQueryStatus.UNKNOWN),
            () -> assertThat(result.data()).isNull(),
            () -> assertThat(result.failureReason()).isEqualTo(PaymentFailureReason.PG_TIMEOUT)
        );
        server.verify();
    }

    @DisplayName("주문 ID로 PG 거래 목록을 조회하면, 주문에 엮인 거래들을 반환한다.")
    @Test
    void returnsOrderTransactions_whenOrderIdExists() {
        // arrange
        server.expect(once(), requestTo(BASE_URL + "/api/v1/payments?orderId=1351039135"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "1"))
            .andRespond(withSuccess("""
                {
                  "meta": {
                    "result": "SUCCESS",
                    "errorCode": null,
                    "message": null
                  },
                  "data": {
                    "orderId": "1351039135",
                    "transactions": [
                      {
                        "transactionKey": "20250816:TR:9577c5",
                        "status": "FAILED",
                        "reason": "한도초과입니다."
                      }
                    ]
                  }
                }
                """, MediaType.APPLICATION_JSON));

        // act
        PaymentGatewayQueryResult<PaymentGatewayOrderTransactions> result = gateway.getTransactionsByOrderId(1L, 1_351_039_135L);

        // assert
        PaymentGatewayOrderTransactions orderTransactions = result.data();
        assertAll(
            () -> assertThat(result.isFound()).isTrue(),
            () -> assertThat(result.status()).isEqualTo(PaymentGatewayQueryStatus.FOUND),
            () -> assertThat(orderTransactions.orderId()).isEqualTo(1_351_039_135L),
            () -> assertThat(orderTransactions.transactions()).hasSize(1),
            () -> assertThat(orderTransactions.transactions().get(0).transactionKey()).isEqualTo("20250816:TR:9577c5"),
            () -> assertThat(orderTransactions.transactions().get(0).status()).isEqualTo(PgPaymentStatus.FAILED),
            () -> assertThat(orderTransactions.transactions().get(0).reason()).isEqualTo("한도초과입니다.")
        );
        server.verify();
    }

    private PgSimulatorProperties properties() {
        return new PgSimulatorProperties(BASE_URL, "http://localhost:8080/api/v1/payments/callback", 1_000L, 10_000L);
    }
}
