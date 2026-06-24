package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 콜백 본문을 신뢰하지 않고 PG 재조회로만 상태를 확정하는지 검증한다(위조 콜백 방어). */
@SpringBootTest
class PaymentCallbackSecurityIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long givenPendingPayment(String transactionKey) {
        OrderModel order = orderRepository.save(
            new OrderModel(USER_ID, List.of(new OrderItem(100L, "후드", 50_000L, 1)), null, Money.ZERO));
        PaymentModel payment = new PaymentModel(order.getId(), USER_ID, CardType.SAMSUNG, order.getFinalAmount());
        payment.assignTransactionKey(transactionKey);
        paymentRepository.save(payment);
        return order.getId();
    }

    @DisplayName("콜백 수신 시")
    @Nested
    class HandleCallback {

        @DisplayName("본문이 아니라 PG 재조회 결과(SUCCESS)로 결제·주문이 확정된다")
        @Test
        void confirmsByGatewayQuery() {
            Long orderId = givenPendingPayment("tx-cb");
            when(paymentGateway.queryStatus(eq("tx-cb"), eq(USER_ID))).thenReturn(Optional.of(new GatewayStatus("SUCCESS", null)));

            paymentFacade.handleCallback("tx-cb");

            assertAll(
                () -> assertThat(paymentRepository.findByTransactionKey("tx-cb").orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("PG가 응답하지 않으면(empty) 콜백을 받아도 PENDING을 유지한다 (자가 확정 안 함)")
        @Test
        void staysPendingWhenGatewayUnavailable() {
            Long orderId = givenPendingPayment("tx-cb");
            when(paymentGateway.queryStatus(eq("tx-cb"), eq(USER_ID))).thenReturn(Optional.empty());

            paymentFacade.handleCallback("tx-cb");

            assertAll(
                () -> assertThat(paymentRepository.findByTransactionKey("tx-cb").orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED)
            );
        }
    }
}
