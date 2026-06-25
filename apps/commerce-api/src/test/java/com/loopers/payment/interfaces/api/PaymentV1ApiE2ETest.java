package com.loopers.payment.interfaces.api;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItems;
import com.loopers.order.domain.OrderService;
import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentGatewayTransaction;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgPaymentStatus;
import com.loopers.shared.presentation.ApiResponse;
import com.loopers.user.interfaces.api.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_PAYMENTS = "/api/v1/payments";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Loopers!2026";
    private static final String TRANSACTION_KEY = "20250816:TR:9577c5";

    private final TestRestTemplate testRestTemplate;
    private final OrderService orderService;
    private final DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired
    PaymentV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        OrderService orderService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.orderService = orderService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class RequestPayment {

        @DisplayName("인증 사용자의 결제 가능한 주문과 카드 정보가 주어지면 201 CREATED와 결제 대기 응답을 반환한다.")
        @Test
        void returnsCreatedPendingPayment_whenAuthenticatedUserRequestsPayment() {
            // arrange
            Long userId = signUpUser();
            Order order = createOrder(userId);
            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(
                order.getId(),
                CardType.SAMSUNG,
                "1234-5678-9814-1451"
            );
            when(paymentGateway.requestPayment(any()))
                .thenReturn(PaymentGatewayResult.accepted(new PaymentGatewayTransaction(
                    TRANSACTION_KEY,
                    PgPaymentStatus.PENDING,
                    null
                )));

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = requestPayment(request, authHeaders());

            // assert
            PaymentV1Dto.PaymentResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(data.id()).isNotNull(),
                () -> assertThat(data.orderId()).isEqualTo(order.getId()),
                () -> assertThat(data.amount()).isEqualTo(1_550_000L),
                () -> assertThat(data.cardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(data.maskedCardNo()).isEqualTo("1234-****-****-1451"),
                () -> assertThat(data.status()).isEqualTo(PaymentStatus.PENDING)
            );
        }

        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthenticationHeadersAreMissing() {
            // arrange
            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(
                1L,
                CardType.SAMSUNG,
                "1234-5678-9814-1451"
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = requestPaymentForError(request, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private Long signUpUser() {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            LOGIN_ID,
            PASSWORD,
            "김성호",
            LocalDate.of(1993, 11, 3),
            "loopers@example.com"
        );
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
            ENDPOINT_USERS,
            HttpMethod.POST,
            new HttpEntity<>(request),
            responseType
        );
        return response.getBody().data().id();
    }

    private Order createOrder(Long userId) {
        OrderItem item = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 1);
        return orderService.saveOrder(Order.create(userId, OrderItems.of(List.of(item))));
    }

    private ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> requestPayment(
        PaymentV1Dto.PaymentRequest request,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PAYMENTS,
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<Object>> requestPaymentForError(
        PaymentV1Dto.PaymentRequest request,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PAYMENTS,
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            responseType
        );
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, PASSWORD);
        return headers;
    }
}
