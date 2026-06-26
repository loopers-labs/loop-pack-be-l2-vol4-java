package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.ZonedDateTime;
import java.util.Map;

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

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentTransactionStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentAdminV1ApiE2ETest {

    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";
    private static final String CARD_NO = "1234-5678-9012-3456";
    private static final String TX_KEY = "TX-0001";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderModel saveOrder() {
        return orderJpaRepository.save(OrderModel.builder()
            .userId(1L)
            .orderedAt(ZonedDateTime.now())
            .originalAmount(78_000)
            .discountAmount(0)
            .finalAmount(78_000)
            .build());
    }

    private PaymentModel savePendingPayment(Long orderId) {
        PaymentModel payment = PaymentModel.builder()
            .orderId(orderId)
            .userId(1L)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo(CARD_NO)
            .requestedAt(ZonedDateTime.now())
            .build();
        payment.recordTransactionKey(TX_KEY);
        return paymentJpaRepository.save(payment);
    }

    private HttpEntity<Void> adminRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LDAP_HEADER, ADMIN_LDAP);
        return new HttpEntity<>(headers);
    }

    private String reconcileEndpoint(Long orderId) {
        return String.format("/api-admin/v1/payments/%d/reconcile", orderId);
    }

    @DisplayName("관리자 결제 수동 복구 - POST /api-admin/v1/payments/{orderId}/reconcile")
    @Nested
    class ReconcilePayment {

        @DisplayName("외부 결제 시스템 조회로 PENDING 결제를 즉시 확정하고 주문을 전이한다.")
        @Test
        void reconcilesPendingPayment_byGatewayQuery() {
            // arrange
            OrderModel order = saveOrder();
            PaymentModel payment = savePendingPayment(order.getId());
            given(paymentGateway.queryTransaction(any()))
                .willReturn(PaymentTransactionStatus.found(TX_KEY, PaymentStatus.SUCCESS, null));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                reconcileEndpoint(order.getId()), HttpMethod.POST, adminRequest(), MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().get("status")).isEqualTo(PaymentStatus.SUCCESS.name()),
                () -> assertThat(paymentJpaRepository.findById(payment.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("관리자 헤더가 없으면 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderMissing() {
            // arrange
            OrderModel order = saveOrder();
            savePendingPayment(order.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                reconcileEndpoint(order.getId()), HttpMethod.POST, HttpEntity.EMPTY, MAP_RESPONSE);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }
    }
}
