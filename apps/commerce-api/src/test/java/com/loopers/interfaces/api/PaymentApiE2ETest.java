package com.loopers.interfaces.api;

import com.loopers.application.user.UserService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.interfaces.api.payment.PaymentDto;
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
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentApiE2ETest {

    private static final String BASE_URL = "/api/v1/payments";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @MockitoBean
    private PgClient pgClient;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UserModel savedUser;
    private OrderModel savedOrder;
    private HttpHeaders userHeaders;

    @BeforeEach
    void setUp() {
        savedUser = userService.signUp(new UserModel(
            "user01", "Password1!", "홍길동",
            LocalDate.of(1990, 1, 1), "user@example.com"
        ));
        savedOrder = orderJpaRepository.save(new OrderModel(savedUser.getId(), null, 10000L, 0L));

        userHeaders = new HttpHeaders();
        userHeaders.set(LOGIN_ID_HEADER, "user01");
        userHeaders.set(LOGIN_PW_HEADER, "Password1!");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class RequestPayment {

        @DisplayName("결제 요청 시, 201과 PENDING 상태의 결제 정보를 반환한다.")
        @Test
        void returns201_whenPaymentCreatedSuccessfully() {
            // arrange
            PaymentDto.CreateRequest request = new PaymentDto.CreateRequest(
                savedOrder.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().status()).isEqualTo("PENDING");
            assertThat(response.getBody().data().transactionKey()).isNull();
        }

        @DisplayName("비로그인 상태로 요청하면, 401을 반환한다.")
        @Test
        void returns401_whenNotLoggedIn() {
            // act
            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(new PaymentDto.CreateRequest(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 수신 시, 200을 반환한다.")
        @Test
        void returns200_onSuccessCallback() {
            // arrange
            PaymentModel payment = new PaymentModel(savedOrder.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.assignTransactionKey("20250626:TR:abc123");
            paymentJpaRepository.save(payment);

            PaymentDto.CallbackRequest request = new PaymentDto.CallbackRequest(
                "20250626:TR:abc123",
                String.format("%012d", savedOrder.getId()),
                "SAMSUNG", "1234-5678-9012-3456", 10000L, "SUCCESS", null
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/callback", HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api/v1/payments/{paymentId}")
    @Nested
    class GetPayment {

        @DisplayName("존재하는 결제를 조회하면, 200과 결제 정보를 반환한다.")
        @Test
        void returns200_whenPaymentExists() {
            // arrange
            PaymentModel payment = paymentJpaRepository.save(
                new PaymentModel(savedOrder.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L)
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + payment.getId(), HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().paymentId()).isEqualTo(payment.getId());
            assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.PENDING.name());
        }

        @DisplayName("존재하지 않는 결제를 조회하면, 404를 반환한다.")
        @Test
        void returns404_whenPaymentNotFound() {
            // act
            ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/999", HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
