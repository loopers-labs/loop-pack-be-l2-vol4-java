package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentGateway;
import com.loopers.application.payment.PaymentGatewayCommand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentPendingReason;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaEntity;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserDto;
import com.loopers.utils.DatabaseCleanUp;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT_PAYMENTS = "/api/v1/payments";
    private static final String ENDPOINT_SIGNUP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final OrderJpaRepository orderJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    PaymentV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        OrderJpaRepository orderJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.orderJpaRepository = orderJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class RequestPayment {

        @DisplayName("주문에 대해 결제를 요청하면, 주문 최종 금액으로 PG 결제를 요청하고 PENDING 결제를 생성한다.")
        @Test
        void createsPendingPayment_whenPaymentIsRequested() {
            // arrange
            signup("user1234", "abc123!?");
            OrderJpaEntity order = saveOrder("user1234", 5_000L);
            PaymentDto.RequestPayment.V1.Request request = new PaymentDto.RequestPayment.V1.Request(
                order.getId(),
                com.loopers.domain.payment.PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451"
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    paymentResponseType()
                );

            // assert
            PaymentDto.RequestPayment.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.userLoginId()).isEqualTo("user1234"),
                () -> assertThat(data.orderId()).isEqualTo(order.getId()),
                () -> assertThat(data.amount()).isEqualTo(5_000L),
                () -> assertThat(data.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(data.pendingReason()).isEqualTo(PaymentPendingReason.WAITING_CALLBACK),
                () -> assertThat(data.transactionKey()).isEqualTo("20260625:TR:test")
            );
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class Callback {

        @DisplayName("PG 성공 콜백을 받으면 PENDING 결제를 PAID로 전이한다.")
        @Test
        void marksPaymentPaid_whenSuccessCallbackIsReceived() {
            // arrange
            signup("user1234", "abc123!?");
            OrderJpaEntity order = saveOrder("user1234", 5_000L);
            requestPayment(order.getId());
            PaymentDto.Callback.V1.Request callback = new PaymentDto.Callback.V1.Request(
                "20260625:TR:test",
                String.valueOf(order.getId()),
                com.loopers.domain.payment.PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451",
                5_000L,
                com.loopers.domain.payment.PaymentGatewayStatus.SUCCESS,
                "정상 승인되었습니다."
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/callback",
                    HttpMethod.POST,
                    new HttpEntity<>(callback),
                    paymentResponseType()
                );

            // assert
            PaymentDto.RequestPayment.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.status()).isEqualTo(PaymentStatus.PAID),
                () -> assertThat(data.pendingReason()).isNull(),
                () -> assertThat(data.transactionKey()).isEqualTo("20260625:TR:test")
            );
        }
    }

    private OrderJpaEntity saveOrder(String userLoginId, Long amount) {
        Order order = new Order(userLoginId, List.of(new OrderLine(1L, "테스트 상품", amount, 1)));
        return orderJpaRepository.save(OrderJpaEntity.from(order));
    }

    private void requestPayment(Long orderId) {
        PaymentDto.RequestPayment.V1.Request request = new PaymentDto.RequestPayment.V1.Request(
            orderId,
            com.loopers.domain.payment.PaymentCardType.SAMSUNG,
            "1234-5678-9814-1451"
        );
        testRestTemplate.exchange(
            ENDPOINT_PAYMENTS,
            HttpMethod.POST,
            new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
            paymentResponseType()
        );
    }

    private void signup(String loginId, String password) {
        UserDto.Register.V1.Request request = new UserDto.Register.V1.Request(
            loginId,
            password,
            "홍길동",
            LocalDate.of(1990, 1, 15),
            loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, String.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private ParameterizedTypeReference<ApiResponse<PaymentDto.RequestPayment.V1.Response>> paymentResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    @TestConfiguration
    static class FakePaymentGatewayConfig {

        @Bean
        @Primary
        PaymentGateway paymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentGatewayResult request(PaymentGatewayCommand command) {
                    return PaymentGatewayResult.pending("20260625:TR:test", null);
                }

                @Override
                public PaymentGatewayResult getByOrder(String userLoginId, Long orderId) {
                    return PaymentGatewayResult.pending("20260625:TR:test", null);
                }
            };
        }
    }
}
