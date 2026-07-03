package com.loopers.interfaces.api.payment;

import com.loopers.domain.order.OrderItemData;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
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
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentReconcileV1ApiE2ETest {

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
                    return Optional.of(new PgTransaction(transactionKey, PaymentStatus.PAID, "정상 승인", 5000L));
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

    private PaymentModel setupUnknown() {
        UserModel user = userRepository.save(new UserModel("user01", "Password1!", "홍길동", "1990-01-01",
                "user@example.com", Gender.MALE, passwordEncryptor));
        OrderModel order = orderRepository.save(OrderModel.create(user.getId(),
                List.of(new OrderItemData(1L, "상품", BigDecimal.valueOf(5000), 1L)), BigDecimal.ZERO));
        PaymentModel payment = PaymentModel.pending(
                order.getId(), order.getOrderNumber(), user.getId(), CardType.SAMSUNG, CARD_NO, 5000L);
        payment.attachTransactionKey("20260626:TR:abc");
        payment.markUnknown();
        return paymentRepository.save(payment);
    }

    private HttpEntity<Void> adminEntity(String ldap) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LDAP, ldap);
        return new HttpEntity<>(null, headers);
    }

    @DisplayName("POST /api/v1/payments/{id}/reconcile")
    @Nested
    class Reconcile {

        @DisplayName("관리자가 UNKNOWN 건을 수동 복구하면 PG 결과대로 PAID 로 확정된다.")
        @Test
        void reconcilesUnknownToPaid_whenAdmin() {
            // given
            PaymentModel payment = setupUnknown();

            // when
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.ReconcileResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PaymentV1Dto.ReconcileResponse>> response = testRestTemplate.exchange(
                    "/api/v1/payments/" + payment.getId() + "/reconcile", HttpMethod.POST,
                    adminEntity(AuthHeaders.ADMIN_LDAP_VALUE), type);

            // then
            PaymentModel reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
            OrderModel order = orderRepository.findByOrderNumber(payment.getOrderNumber()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(response.getBody().data().outcome()).isEqualTo("PAID"),
                    () -> assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PAID),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("관리자 권한이 없으면 403 을 반환한다.")
        @Test
        void returns403_whenNotAdmin() {
            // given
            PaymentModel payment = setupUnknown();

            // when
            ResponseEntity<Void> response = testRestTemplate.exchange(
                    "/api/v1/payments/" + payment.getId() + "/reconcile", HttpMethod.POST,
                    adminEntity("not-admin"), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
