package com.loopers.interfaces.api.payment;

import com.loopers.domain.order.OrderItemData;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/payments";
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";
    private static final String CARD_NO = "1234-5678-9814-1451";

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        PaymentGateway fakeGateway() {
            return new PaymentGateway() {
                @Override
                public PgTransaction request(PgPaymentCommand command) {
                    return new PgTransaction("20260626:TR:e2e", PaymentStatus.PENDING, null, null);
                }

                @Override
                public Optional<PgTransaction> findByTransactionKey(String transactionKey) {
                    return Optional.empty();
                }

                @Override
                public List<PgTransaction> findByOrderId(String orderNumber) {
                    return List.of();
                }
            };
        }
    }

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PasswordEncryptor passwordEncryptor;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private UserModel saveUser(String loginId, String email) {
        return userRepository.save(new UserModel(loginId, LOGIN_PW, "홍길동", "1990-01-01", email, Gender.MALE, passwordEncryptor));
    }

    private OrderModel saveOrder(Long userId) {
        return orderRepository.save(OrderModel.create(userId,
                List.of(new OrderItemData(1L, "상품", BigDecimal.valueOf(5000), 1L)), BigDecimal.ZERO));
    }

    private <T> HttpEntity<T> authJson(T body, String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
        return new HttpEntity<>(body, headers);
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class Pay {

        @DisplayName("유효한 요청이면 202 와 PENDING 응답을 반환하고 결제가 PENDING 으로 영속된다.")
        @Test
        void returns202AndPersistsPending_whenValid() {
            // given
            UserModel user = saveUser(LOGIN_ID, "user@example.com");
            OrderModel order = saveOrder(user.getId());
            PaymentV1Dto.PaymentRequest request =
                    new PaymentV1Dto.PaymentRequest(order.getOrderNumber(), CardType.SAMSUNG, CARD_NO);

            // when
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJson(request, LOGIN_ID), type);

            // then
            Long paymentId = response.getBody().data().paymentId();
            PaymentModel saved = paymentRepository.findById(paymentId).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING"),
                    () -> assertThat(response.getBody().data().orderNumber()).isEqualTo(order.getOrderNumber()),
                    () -> assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(saved.getUserId()).isEqualTo(user.getId())
            );
        }

        @DisplayName("카드 번호 형식이 올바르지 않으면 400 을 반환한다.")
        @Test
        void returns400_whenCardNoInvalid() {
            // given
            UserModel user = saveUser(LOGIN_ID, "user@example.com");
            OrderModel order = saveOrder(user.getId());
            PaymentV1Dto.PaymentRequest request =
                    new PaymentV1Dto.PaymentRequest(order.getOrderNumber(), CardType.SAMSUNG, "invalid-card");

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJson(request, LOGIN_ID), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/payments/{id}")
    @Nested
    class GetPayment {

        @DisplayName("본인 결제를 조회하면 현재 상태가 노출된다.")
        @Test
        void exposesStatus_whenOwner() {
            // given
            UserModel user = saveUser(LOGIN_ID, "user@example.com");
            OrderModel order = saveOrder(user.getId());
            PaymentModel payment = paymentRepository.save(PaymentModel.pending(
                    order.getId(), order.getOrderNumber(), user.getId(), CardType.SAMSUNG, CARD_NO, 5000L));

            // when
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentStatusResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentStatusResponse>> response = testRestTemplate.exchange(
                    BASE_URL + "/" + payment.getId(), HttpMethod.GET, authJson(null, LOGIN_ID), type);

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(response.getBody().data().paymentId()).isEqualTo(payment.getId()),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING")
            );
        }

        @DisplayName("타인의 결제를 조회하면 403 을 반환한다.")
        @Test
        void returns403_whenNotOwner() {
            // given
            UserModel owner = saveUser(LOGIN_ID, "user@example.com");
            saveUser("other01", "other@example.com");
            OrderModel order = saveOrder(owner.getId());
            PaymentModel payment = paymentRepository.save(PaymentModel.pending(
                    order.getId(), order.getOrderNumber(), owner.getId(), CardType.SAMSUNG, CARD_NO, 5000L));

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    BASE_URL + "/" + payment.getId(), HttpMethod.GET, authJson(null, "other01"), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
