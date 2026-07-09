package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentFacadeTest {

    @Autowired
    private PaymentFacade paymentFacade;

    @MockitoBean
    private PgClient pgClient;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제를 요청할 때,")
    @Nested
    class RequestPayment {

        @DisplayName("PENDING 상태로 결제가 생성된다.")
        @Test
        void requestPayment_createsPendingPayment() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));

            // act
            paymentFacade.requestPayment(1L, order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            // assert
            PaymentModel payment = paymentJpaRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getTransactionKey()).isNull();
        }
    }

    @DisplayName("PG 콜백을 처리할 때,")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 수신 시, 결제가 SUCCESS로 변경되고 주문이 CONFIRMED된다.")
        @Test
        void handleCallback_successesPaymentAndConfirmsOrder() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            PaymentModel payment = new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.assignTransactionKey("20250626:TR:abc123");
            paymentJpaRepository.save(payment);

            // act
            paymentFacade.handleCallback("20250626:TR:abc123", "SUCCESS", null);

            // assert
            PaymentModel updatedPayment = paymentJpaRepository.findByTransactionKey("20250626:TR:abc123").orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @DisplayName("FAILED 콜백 수신 시, 결제가 FAILED로 변경되고 주문이 CANCELLED된다.")
        @Test
        void handleCallback_failsPaymentAndCancelsOrder() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            PaymentModel payment = new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.assignTransactionKey("20250626:TR:abc123");
            paymentJpaRepository.save(payment);

            // act
            paymentFacade.handleCallback("20250626:TR:abc123", "FAILED", "한도 초과");

            // assert
            PaymentModel updatedPayment = paymentJpaRepository.findByTransactionKey("20250626:TR:abc123").orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(updatedPayment.getFailureReason()).isEqualTo("한도 초과");

            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }
}
