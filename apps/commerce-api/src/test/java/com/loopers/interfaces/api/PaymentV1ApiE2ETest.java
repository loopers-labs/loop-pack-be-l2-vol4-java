package com.loopers.interfaces.api;

import com.loopers.application.payment.PgDto;
import com.loopers.application.payment.PgPaymentClient;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    // 외부 시스템(PG)은 mock — HTTP 경로(컨트롤러→Facade→Service→repo)만 검증
    @MockitoBean
    private PgPaymentClient pgPaymentClient;

    private Long userId;

    @Autowired
    public PaymentV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        OrderJpaRepository orderJpaRepository,
        PaymentJpaRepository paymentJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.paymentJpaRepository = paymentJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        User user = userJpaRepository.save(
            new User("tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M));
        this.userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long persistPendingOrder(long amount) {
        Order order = new Order(userId, List.of(new OrderItem(10L, "에어맥스", Money.of(amount), Quantity.of(1))));
        return orderJpaRepository.save(order).getId();
    }

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.HEADER_LOGIN_ID, loginId);
        headers.set(AuthHeaders.HEADER_LOGIN_PW, "Password1!");
        return headers;
    }

    private ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> requestPayment(String loginId, Long orderId) {
        PaymentV1Dto.PaymentRequest request =
            new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
        ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders(loginId)), responseType);
    }

    private PgDto.Envelope<PgDto.TransactionResponse> pgSuccess(String key) {
        return new PgDto.Envelope<>(new PgDto.Envelope.Meta("SUCCESS", null, null),
            new PgDto.TransactionResponse(key, "PENDING", null));
    }

    private PgDto.Envelope<PgDto.TransactionResponse> pgUnavailable() {
        return new PgDto.Envelope<>(new PgDto.Envelope.Meta("FAIL", "PG_UNAVAILABLE", "미확정"), null);
    }

    private Payment activePaymentOf(Long orderId) {
        return paymentJpaRepository.findFirstByOrderIdAndStatusInOrderByIdDesc(
            orderId, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS)).orElseThrow();
    }

    private void persistPendingPayment(Long orderId, String transactionKey) {
        Payment payment = Payment.pending(userId, orderId, Money.of(5000L), CardType.SAMSUNG);
        payment.assignTransactionKey(transactionKey);
        paymentJpaRepository.save(payment);
    }

    private ResponseEntity<ApiResponse<Object>> postCallback(String transactionKey, String status, String reason) {
        PaymentV1Dto.PgCallbackRequest body = new PaymentV1Dto.PgCallbackRequest(
            transactionKey, "000001", "SAMSUNG", "1234-5678-9814-1451", 5000L, status, reason);
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT + "/callback", HttpMethod.POST, new HttpEntity<>(body), responseType);
    }

    private PgDto.Envelope<PgDto.TransactionDetailResponse> detailSuccess(String transactionKey) {
        return new PgDto.Envelope<>(new PgDto.Envelope.Meta("SUCCESS", null, null),
            new PgDto.TransactionDetailResponse(
                transactionKey, "000001", "SAMSUNG", "1234-5678-9814-1451", 5000L, "SUCCESS", null));
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class RequestPayment {

        @DisplayName("PG가 거래키를 반환하면, PENDING ACK·거래키를 반환하고 결제가 PENDING(키 포함)으로 저장된다.")
        @Test
        void acceptsPayment_onPgSuccess() {
            // arrange
            Long orderId = persistPendingOrder(5000L);
            when(pgPaymentClient.requestPayment(any(), any())).thenReturn(pgSuccess("TKEY-1"));

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = requestPayment("tester01", orderId);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING"),
                () -> assertThat(response.getBody().data().transactionKey()).isEqualTo("TKEY-1"),
                () -> assertThat(response.getBody().data().amount()).isEqualTo(5000L)
            );
            assertThat(activePaymentOf(orderId).getTransactionKey()).isEqualTo("TKEY-1");
        }

        @DisplayName("PG 호출이 미확정(fallback)이면, PENDING ACK(거래키 없음)를 반환하고 결제는 PENDING으로 남는다.")
        @Test
        void keepsPending_onFallback() {
            // arrange
            Long orderId = persistPendingOrder(5000L);
            when(pgPaymentClient.requestPayment(any(), any())).thenReturn(pgUnavailable());

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = requestPayment("tester01", orderId);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING"),
                () -> assertThat(response.getBody().data().transactionKey()).isNull()
            );
            assertThat(activePaymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("존재하지 않는 주문이면, 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenOrderMissing() {
            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = requestPayment("tester01", 99999L);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api/v1/payments/callback (PG 콜백)")
    @Nested
    class Callback {

        @DisplayName("SUCCESS 콜백이면, 결제 SUCCESS·주문 PAID 로 반영된다.")
        @Test
        void successCallback() {
            // arrange
            Long orderId = persistPendingOrder(5000L);
            persistPendingPayment(orderId, "TKEY-CB-1");

            // act
            ResponseEntity<ApiResponse<Object>> response = postCallback("TKEY-CB-1", "SUCCESS", null);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(paymentJpaRepository.findByTransactionKey("TKEY-CB-1").orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("FAILED 콜백이면, 결제·주문 FAILED 로 반영된다.")
        @Test
        void failedCallback() {
            // arrange
            Long orderId = persistPendingOrder(5000L);
            persistPendingPayment(orderId, "TKEY-CB-2");

            // act
            ResponseEntity<ApiResponse<Object>> response = postCallback("TKEY-CB-2", "FAILED", "한도 초과");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(paymentJpaRepository.findByTransactionKey("TKEY-CB-2").orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.FAILED)
            );
        }

        @DisplayName("중복 콜백이 와도, 상태는 한 번만 확정된다. (멱등)")
        @Test
        void duplicateCallback_isIdempotent() {
            // arrange
            Long orderId = persistPendingOrder(5000L);
            persistPendingPayment(orderId, "TKEY-CB-3");

            // act
            postCallback("TKEY-CB-3", "SUCCESS", null);
            ResponseEntity<ApiResponse<Object>> second = postCallback("TKEY-CB-3", "FAILED", "뒤늦은 실패");

            // assert — 두 번째(상충) 콜백은 무시되고 SUCCESS/PAID 유지
            assertAll(
                () -> assertThat(second.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(paymentJpaRepository.findByTransactionKey("TKEY-CB-3").orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }
    }

    @DisplayName("POST /api/v1/payments/{id}/reconcile (수동 복구)")
    @Nested
    class ManualReconcile {

        @DisplayName("PENDING 결제를 PG에 조회해 SUCCESS면 결제 SUCCESS·주문 PAID 로 보정한다.")
        @Test
        void reconcilesPending() {
            // arrange — 콜백이 안 와 PENDING 으로 남은 결제(거래키 보유)
            Long orderId = persistPendingOrder(5000L);
            persistPendingPayment(orderId, "TKEY-RC");
            Long paymentId = activePaymentOf(orderId).getId();
            when(pgPaymentClient.getTransaction(anyString(), eq("TKEY-RC"))).thenReturn(detailSuccess("TKEY-RC"));

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + paymentId + "/reconcile", HttpMethod.POST,
                new HttpEntity<>(authHeaders("tester01")), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(paymentJpaRepository.findByTransactionKey("TKEY-RC").orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("타 유저의 결제를 복구 시도하면, 404 Not Found 를 반환한다.")
        @Test
        void returnsNotFound_whenNotOwner() {
            // arrange
            userJpaRepository.save(new User("tester02", "Password2!", "김철수", "1992-03-03", "o@example.com", Gender.M));
            Long orderId = persistPendingOrder(5000L);
            persistPendingPayment(orderId, "TKEY-RC2");
            Long paymentId = activePaymentOf(orderId).getId();

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + paymentId + "/reconcile", HttpMethod.POST,
                new HttpEntity<>(authHeaders("tester02")), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
