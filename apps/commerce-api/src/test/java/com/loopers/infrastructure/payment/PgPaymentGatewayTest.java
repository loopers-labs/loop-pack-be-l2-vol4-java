package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentGatewayTransaction;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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

    @DisplayName("결제 상태를 조회할 때, ")
    @Nested
    class GetTransaction {

        private static final String TRANSACTION_KEY = "20260624:TR:abc123";

        @DisplayName("transactionKey 로 PG 에 X-USER-ID 헤더를 담아 GET 한다.")
        @Test
        void sendsGet_withUserIdHeader() {
            // given
            server.expect(requestTo(BASE_URL + "/api/v1/payments/" + TRANSACTION_KEY))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-USER-ID", "1"))
                .andRespond(withSuccess(detailResponseJson("SUCCESS", "정상 승인되었습니다."), MediaType.APPLICATION_JSON));

            // when
            gateway.getTransaction(1L, TRANSACTION_KEY);

            // then
            server.verify();
        }

        @DisplayName("PG 가 확정 상태(status·reason)를 응답하면, 그 값을 조회 결과로 반환한다.")
        @Test
        void returnsStatusAndReason_whenPgResponds() {
            // given
            server.expect(requestTo(BASE_URL + "/api/v1/payments/" + TRANSACTION_KEY))
                .andRespond(withSuccess(detailResponseJson("SUCCESS", "정상 승인되었습니다."), MediaType.APPLICATION_JSON));

            // when
            PaymentGatewayTransaction transaction = gateway.getTransaction(1L, TRANSACTION_KEY);

            // then
            assertThat(transaction.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(transaction.reason()).isEqualTo("정상 승인되었습니다.");
        }
    }

    @DisplayName("주문 ID 로 결제 트랜잭션 목록을 조회할 때, ")
    @Nested
    class GetTransactionsByOrder {

        @DisplayName("orderId 를 zero-pad 한 쿼리로 X-USER-ID 헤더를 담아 GET 한다.")
        @Test
        void sendsGet_withUserIdHeaderAndEncodedOrderId() {
            // given
            server.expect(requestTo(BASE_URL + "/api/v1/payments?orderId=000100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-USER-ID", "1"))
                .andRespond(withSuccess(orderResponseJson(), MediaType.APPLICATION_JSON));

            // when
            gateway.getTransactionsByOrder(1L, 100L);

            // then
            server.verify();
        }

        @DisplayName("PG 가 트랜잭션 목록을 응답하면, key·status·reason 을 보존해 반환한다.")
        @Test
        void returnsTransactions_whenPgResponds() {
            // given
            server.expect(requestTo(BASE_URL + "/api/v1/payments?orderId=000100"))
                .andRespond(withSuccess(orderResponseJson(), MediaType.APPLICATION_JSON));

            // when
            List<PaymentGatewayTransaction> transactions = gateway.getTransactionsByOrder(1L, 100L);

            // then
            assertThat(transactions).hasSize(2);
            assertThat(transactions.get(0).transactionKey()).isEqualTo("key-success");
            assertThat(transactions.get(0).status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(transactions.get(0).reason()).isEqualTo("정상 승인되었습니다.");
            assertThat(transactions.get(1).transactionKey()).isEqualTo("key-pending");
            assertThat(transactions.get(1).status()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("PG 가 해당 주문에 결제가 없다고 404 로 응답하면, 빈 목록으로 번역한다.")
        @Test
        void returnsEmpty_whenPgRespondsNotFound() {
            // given
            server.expect(requestTo(BASE_URL + "/api/v1/payments?orderId=000100"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

            // when
            List<PaymentGatewayTransaction> transactions = gateway.getTransactionsByOrder(1L, 100L);

            // then
            assertThat(transactions).isEmpty();
        }
    }

    private String orderResponseJson() {
        return """
            {
              "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
              "data": {
                "orderId": "000100",
                "transactions": [
                  { "transactionKey": "key-success", "status": "SUCCESS", "reason": "정상 승인되었습니다." },
                  { "transactionKey": "key-pending", "status": "PENDING", "reason": null }
                ]
              }
            }
            """;
    }

    private String pendingResponseJson() {
        return """
            {
              "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
              "data": { "transactionKey": "20260624:TR:abc123", "status": "PENDING", "reason": null }
            }
            """;
    }

    private String detailResponseJson(String status, String reason) {
        return """
            {
              "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
              "data": {
                "transactionKey": "20260624:TR:abc123",
                "orderId": "000100",
                "cardType": "SAMSUNG",
                "cardNo": "1234-5678-9814-1451",
                "amount": 50000,
                "status": "%s",
                "reason": "%s"
              }
            }
            """.formatted(status, reason);
    }
}
