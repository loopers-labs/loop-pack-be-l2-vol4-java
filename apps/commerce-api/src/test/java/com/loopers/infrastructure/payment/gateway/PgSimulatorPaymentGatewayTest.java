package com.loopers.infrastructure.payment.gateway;

import com.loopers.domain.payment.gateway.PaymentGatewayCommand;
import com.loopers.domain.payment.gateway.PaymentGatewayResult;
import com.loopers.domain.payment.gateway.PaymentGatewayTransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PgSimulatorPaymentGatewayTest {

    @DisplayName("PG 결제 요청은 simulator 요청/응답을 내부 PENDING 결과로 매핑한다.")
    @Test
    void requestsPaymentAndMapsPendingResponse() {
        // arrange
        TestFixture fixture = new TestFixture();
        fixture.server.expect(requestTo("http://pg.test/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-USER-ID", "user1"))
            .andExpect(content().json("""
                {
                  "orderId": "000001",
                  "cardType": "SAMSUNG",
                  "cardNo": "1234-5678-9814-1451",
                  "amount": 5000,
                  "callbackUrl": "http://commerce.test/api/v1/payments/callback"
                }
                """))
            .andRespond(withSuccess("""
                {
                  "meta": { "result": "SUCCESS" },
                  "data": {
                    "transactionKey": "20250816:TR:9577c5",
                    "status": "PENDING",
                    "reason": null
                  }
                }
                """, MediaType.APPLICATION_JSON));

        // act
        PaymentGatewayResult result = fixture.gateway.requestPayment(new PaymentGatewayCommand.Request(
            "user1",
            "000001",
            "SAMSUNG",
            "1234-5678-9814-1451",
            5_000L,
            "ignored-by-adapter"
        ));

        // assert
        assertAll(
            () -> assertThat(result.transactionKey()).isEqualTo("20250816:TR:9577c5"),
            () -> assertThat(result.transactionStatus()).isEqualTo(PaymentGatewayTransactionStatus.PENDING),
            () -> assertThat(result.orderId()).isEqualTo("000001"),
            () -> assertThat(result.success()).isTrue()
        );
        fixture.server.verify();
    }

    @DisplayName("PG transactionKey 조회는 최종 SUCCESS 결과를 내부 결과로 매핑한다.")
    @Test
    void getsPaymentByTransactionKeyAndMapsSuccessResponse() {
        // arrange
        TestFixture fixture = new TestFixture();
        fixture.server.expect(requestTo("http://pg.test/api/v1/payments/20250816:TR:9577c5"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "user1"))
            .andRespond(withSuccess("""
                {
                  "meta": { "result": "SUCCESS" },
                  "data": {
                    "transactionKey": "20250816:TR:9577c5",
                    "orderId": "000001",
                    "cardType": "SAMSUNG",
                    "cardNo": "1234-5678-9814-1451",
                    "amount": 5000,
                    "status": "SUCCESS",
                    "reason": null
                  }
                }
                """, MediaType.APPLICATION_JSON));

        // act
        PaymentGatewayResult result = fixture.gateway.getPayment("user1", "20250816:TR:9577c5").orElseThrow();

        // assert
        assertAll(
            () -> assertThat(result.transactionKey()).isEqualTo("20250816:TR:9577c5"),
            () -> assertThat(result.transactionStatus()).isEqualTo(PaymentGatewayTransactionStatus.SUCCESS),
            () -> assertThat(result.orderId()).isEqualTo("000001"),
            () -> assertThat(result.success()).isTrue()
        );
        fixture.server.verify();
    }

    @DisplayName("PG orderId 조회는 최종 FAILED 결과와 실패 사유를 내부 결과로 매핑한다.")
    @Test
    void getsPaymentsByOrderIdAndMapsFailedResponse() {
        // arrange
        TestFixture fixture = new TestFixture();
        fixture.server.expect(requestTo("http://pg.test/api/v1/payments?orderId=000001"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "user1"))
            .andRespond(withSuccess("""
                {
                  "meta": { "result": "SUCCESS" },
                  "data": {
                    "orderId": "000001",
                    "transactions": [
                      {
                        "transactionKey": "20250816:TR:failed",
                        "status": "FAILED",
                        "reason": "LIMIT_EXCEEDED"
                      }
                    ]
                  }
                }
                """, MediaType.APPLICATION_JSON));

        // act
        List<PaymentGatewayResult> results = fixture.gateway.getPaymentsByOrderId("user1", "000001");

        // assert
        assertThat(results).hasSize(1);
        PaymentGatewayResult result = results.get(0);
        assertAll(
            () -> assertThat(result.transactionKey()).isEqualTo("20250816:TR:failed"),
            () -> assertThat(result.transactionStatus()).isEqualTo(PaymentGatewayTransactionStatus.FAILED),
            () -> assertThat(result.orderId()).isEqualTo("000001"),
            () -> assertThat(result.message()).isEqualTo("LIMIT_EXCEEDED"),
            () -> assertThat(result.success()).isFalse()
        );
        fixture.server.verify();
    }

    private static class TestFixture {
        private final RestTemplate restTemplate = new RestTemplate();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        private final PgSimulatorProperties properties = properties();
        private final PgSimulatorPaymentGateway gateway = new PgSimulatorPaymentGateway(restTemplate, properties);

        private static PgSimulatorProperties properties() {
            PgSimulatorProperties properties = new PgSimulatorProperties();
            properties.setBaseUrl("http://pg.test");
            properties.setCallbackUrl("http://commerce.test/api/v1/payments/callback");
            return properties;
        }
    }
}
