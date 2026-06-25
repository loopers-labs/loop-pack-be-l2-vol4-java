package com.loopers.payment.interfaces;

import com.loopers.order.domain.OrderStatus;
import com.loopers.order.infrastructure.OrderJpaRepository;
import com.loopers.payment.domain.PaymentModel;
import com.loopers.payment.infrastructure.PaymentJpaRepository;
import com.loopers.payment.infrastructure.pg.PgPaymentClient;
import com.loopers.payment.infrastructure.pg.PgPaymentClientDto;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.infrastructure.StockJpaRepository;
import com.loopers.support.response.ApiResponse;
import com.loopers.user.domain.Gender;
import com.loopers.user.interfaces.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String LOGIN_ID = "user1";
    private static final String PASSWORD = "Pass123!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private PgPaymentClient pgPaymentClient;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.SignUpRequest(LOGIN_ID, PASSWORD, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)),
            Void.class
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    private Long savedOrderId() {
        ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "운동화", 150000L, null));
        stockJpaRepository.save(new StockModel(product.getId(), 100));

        ParameterizedTypeReference<ApiResponse<Map<String, Object>>> type = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
            "/api/v1/orders", HttpMethod.POST,
            new HttpEntity<>(Map.of("items", List.of(Map.of("productId", product.getId(), "quantity", 1))), authHeaders()),
            type
        );
        return Long.valueOf(response.getBody().data().get("id").toString());
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class RequestPayment {

        @DisplayName("정상 결제 요청이면, 200 OK와 transactionKey를 반환한다.")
        @Test
        void returnsTransactionKey_whenValidRequest() {
            // arrange
            Long orderId = savedOrderId();
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");

            // act
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), type);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().transactionKey()).isEqualTo("TX-001234")
            );
        }

        @DisplayName("존재하지 않는 orderId이면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenOrderNotExists() {
            // arrange
            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(999L, "SAMSUNG", "1234-5678-9012-3456");

            // act
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), Void.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("PENDING_PAYMENT가 아닌 주문이면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenOrderIsNotPendingPayment() {
            // arrange
            Long orderId = savedOrderId();
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");
            // 첫 번째 결제 요청으로 IN_PAYMENT 상태로 만들기
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), Void.class);
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001235", "PENDING", null));

            // act
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), Void.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class HandleCallback {

        @DisplayName("콜백 수신 시 PG 재조회 결과가 SUCCESS이면, 200 OK를 반환하고 주문이 CONFIRMED된다.")
        @Test
        void returnsOk_andConfirmsOrder_whenPgReturnsSuccess() {
            // arrange
            Long orderId = savedOrderId();
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"), authHeaders()), Void.class);
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "SUCCESS", "정상 승인되었습니다."));

            PaymentV1Dto.CallbackRequest callback = new PaymentV1Dto.CallbackRequest("TX-001234", orderId);

            // act
            ResponseEntity<Void> response = testRestTemplate.exchange(
                ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(callback), Void.class);

            // assert
            Long paymentOrderId = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow().getOrderId();
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(orderJpaRepository.findById(paymentOrderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CONFIRMED)
            );
        }

        @DisplayName("콜백 수신 시 PG 재조회 결과가 FAILED이면, 200 OK를 반환하고 주문이 PAYMENT_FAILED된다.")
        @Test
        void returnsOk_andFailsOrder_whenPgReturnsFailed() {
            // arrange
            Long orderId = savedOrderId();
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"), authHeaders()), Void.class);
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "FAILED", "한도초과입니다."));

            PaymentV1Dto.CallbackRequest callback = new PaymentV1Dto.CallbackRequest("TX-001234", orderId);

            // act
            ResponseEntity<Void> response = testRestTemplate.exchange(
                ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(callback), Void.class);

            // assert
            Long paymentOrderId = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow().getOrderId();
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(orderJpaRepository.findById(paymentOrderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }

        @DisplayName("콜백 바디의 status를 위조해도, PG 재조회 결과(FAILED)를 따른다.")
        @Test
        void followsPgResult_whenCallbackStatusIsForged() {
            // arrange
            Long orderId = savedOrderId();
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"), authHeaders()), Void.class);
            // PG의 실제 상태는 FAILED이지만, 콜백 DTO에는 status 필드 자체가 없어 위조할 방법이 없다
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "FAILED", "한도초과입니다."));

            PaymentV1Dto.CallbackRequest callback = new PaymentV1Dto.CallbackRequest("TX-001234", orderId);

            // act
            testRestTemplate.exchange(ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(callback), Void.class);

            // assert
            assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_FAILED);
        }

        @DisplayName("중복 콜백이면, 200 OK를 반환하고 무시된다.")
        @Test
        void returnsOk_andIgnores_whenDuplicateCallback() {
            // arrange
            Long orderId = savedOrderId();
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"), authHeaders()), Void.class);
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "SUCCESS", "정상 승인되었습니다."));

            PaymentV1Dto.CallbackRequest callback = new PaymentV1Dto.CallbackRequest("TX-001234", orderId);
            testRestTemplate.exchange(ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(callback), Void.class);

            // act (두 번째 콜백)
            ResponseEntity<Void> response = testRestTemplate.exchange(
                ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(callback), Void.class);

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }

    @DisplayName("POST /api/v1/payments/{orderId}/recover")
    @Nested
    class RecoverPayment {

        @DisplayName("IN_PAYMENT 주문이고 PG가 SUCCESS 반환하면, 200 OK를 반환하고 Order가 CONFIRMED된다.")
        @Test
        void returnsOk_andConfirmsOrder_whenPgReturnsSuccess() {
            // arrange
            Long orderId = savedOrderId();
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"), authHeaders()), Void.class);
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "SUCCESS", "정상 승인되었습니다."));

            // act
            ResponseEntity<Void> response = testRestTemplate.exchange(
                ENDPOINT + "/" + orderId + "/recover", HttpMethod.POST, new HttpEntity<>(authHeaders()), Void.class);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CONFIRMED)
            );
        }

        @DisplayName("존재하지 않는 orderId이면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenPaymentNotExists() {
            // act
            ResponseEntity<Void> response = testRestTemplate.exchange(
                ENDPOINT + "/999/recover", HttpMethod.POST, new HttpEntity<>(authHeaders()), Void.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("본인 주문이 아니면, 403을 반환한다.")
        @Test
        void returnsForbidden_whenNotOrderOwner() {
            // arrange
            Long orderId = savedOrderId();
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(new PaymentV1Dto.PaymentRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"), authHeaders()), Void.class);

            String otherLoginId = "user2";
            testRestTemplate.exchange(
                "/api/v1/users", HttpMethod.POST,
                new HttpEntity<>(new UserV1Dto.SignUpRequest(otherLoginId, PASSWORD, "임꺽정", "test2@example.com", "2000-01-01", Gender.MALE)),
                Void.class
            );
            HttpHeaders otherUserHeaders = new HttpHeaders();
            otherUserHeaders.set("X-Loopers-LoginId", otherLoginId);
            otherUserHeaders.set("X-Loopers-LoginPw", PASSWORD);

            // act
            ResponseEntity<Void> response = testRestTemplate.exchange(
                ENDPOINT + "/" + orderId + "/recover", HttpMethod.POST, new HttpEntity<>(otherUserHeaders), Void.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
