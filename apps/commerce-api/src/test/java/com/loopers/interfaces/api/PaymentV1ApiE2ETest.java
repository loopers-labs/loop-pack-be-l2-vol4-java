package com.loopers.interfaces.api;

import com.loopers.application.payment.PgDto;
import com.loopers.application.payment.PgPaymentClient;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
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
}
