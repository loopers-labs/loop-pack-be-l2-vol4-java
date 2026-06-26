package com.loopers.interfaces.api.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "pg.base-url=http://localhost:18084")
class PaymentV1ApiE2ETest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().port(18084))
        .build();

    private final TestRestTemplate rest;
    private final UserJpaRepository userJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    PaymentV1ApiE2ETest(TestRestTemplate rest, UserJpaRepository userJpaRepository,
                        OrderJpaRepository orderJpaRepository, DatabaseCleanUp databaseCleanUp) {
        this.rest = rest;
        this.userJpaRepository = userJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        wiremock.resetAll();
    }

    private static final String LOGIN_ID = "hajin01";
    private static final String LOGIN_PW = "pw1";
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String TX_KEY = "20260626:TR:abc123";

    private OrderModel setupUserAndOrder() {
        UserModel user = userJpaRepository.save(new UserModel(LOGIN_ID, LOGIN_PW));
        return orderJpaRepository.save(new OrderModel(user.getId(), 5000L, 0L, 5000L, null));
    }

    private void stubPgSuccess() {
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments")).willReturn(okJson(
            "{\"meta\":{\"result\":\"SUCCESS\"},\"data\":{\"transactionKey\":\"" + TX_KEY + "\",\"status\":\"PENDING\",\"reason\":null}}"
        )));
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", LOGIN_PW);
        return headers;
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class RequestPayment {

        @DisplayName("결제를 요청하면, 200과 함께 transactionKey/PENDING을 반환한다.")
        @Test
        void returnsTransactionKey() {
            OrderModel order = setupUserAndOrder();
            stubPgSuccess();
            Map<String, Object> body = Map.of("orderId", order.getId(), "cardType", "SAMSUNG", "cardNo", CARD_NO);

            ResponseEntity<JsonNode> res = rest.exchange(
                "/api/v1/payments", HttpMethod.POST, new HttpEntity<>(body, authHeaders()), JsonNode.class);

            assertAll(
                () -> assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(res.getBody().get("data").get("transactionKey").asText()).isEqualTo(TX_KEY),
                () -> assertThat(res.getBody().get("data").get("status").asText()).isEqualTo("PENDING")
            );
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class Callback {

        @DisplayName("SUCCESS 콜백이면, 200을 반환하고 주문이 PAID가 된다.")
        @Test
        void confirmsOrder_onSuccessCallback() {
            OrderModel order = setupUserAndOrder();
            stubPgSuccess();
            rest.exchange("/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(Map.of("orderId", order.getId(), "cardType", "SAMSUNG", "cardNo", CARD_NO), authHeaders()),
                JsonNode.class);

            Map<String, Object> callback = Map.of(
                "transactionKey", TX_KEY, "orderId", String.valueOf(order.getId()),
                "cardType", "SAMSUNG", "cardNo", CARD_NO, "amount", 5000, "status", "SUCCESS", "reason", "정상 승인되었습니다.");
            HttpHeaders json = new HttpHeaders();
            json.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<JsonNode> res = rest.exchange(
                "/api/v1/payments/callback", HttpMethod.POST, new HttpEntity<>(callback, json), JsonNode.class);

            assertAll(
                () -> assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }
    }
}
