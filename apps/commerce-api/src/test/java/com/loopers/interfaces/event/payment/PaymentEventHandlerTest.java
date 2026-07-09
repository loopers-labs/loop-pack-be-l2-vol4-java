package com.loopers.interfaces.event.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.pg.PgResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.SocketTimeoutException;

import org.springframework.test.util.AopTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentEventHandlerTest {

    @Autowired
    private PaymentEventHandler paymentEventHandler;

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

    @DisplayName("결제 이벤트를 처리할 때,")
    @Nested
    class Handle {

        @DisplayName("PG가 200으로 응답하면, PENDING 상태를 유지하고 transactionKey가 저장된다.")
        @Test
        void handle_savesTransactionKey_whenPgSucceeds() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            PaymentModel payment = paymentJpaRepository.save(new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            when(pgClient.createTransaction(any(), any()))
                .thenReturn(new PgResponse.TransactionResponse("20250626:TR:abc123", "PENDING", null));

            PaymentRequestedEvent event = PaymentRequestedEvent.of(payment, 1L);

            // act
            AopTestUtils.<PaymentEventHandler>getTargetObject(paymentEventHandler).handle(event);

            // assert
            PaymentModel updated = paymentJpaRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(updated.getTransactionKey()).isEqualTo("20250626:TR:abc123");
        }

        @DisplayName("PG 타임아웃이 발생하면, PENDING 상태를 유지한다.")
        @Test
        void handle_keepsPending_whenPgTimesOut() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            PaymentModel payment = paymentJpaRepository.save(new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            when(pgClient.createTransaction(any(), any()))
                .thenThrow(new RuntimeException(new SocketTimeoutException("Read timed out")));

            PaymentRequestedEvent event = PaymentRequestedEvent.of(payment, 1L);

            // act
            AopTestUtils.<PaymentEventHandler>getTargetObject(paymentEventHandler).handle(event);

            // assert
            PaymentModel updated = paymentJpaRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(updated.getTransactionKey()).isNull();
        }

        @DisplayName("PG 500 에러가 발생하면, 결제가 FAILED 처리되고 주문이 CANCELLED된다.")
        @Test
        void handle_failsPaymentAndCancelsOrder_whenPgReturns500() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            PaymentModel payment = paymentJpaRepository.save(new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            when(pgClient.createTransaction(any(), any()))
                .thenThrow(new RuntimeException("PG 서버 오류"));

            PaymentRequestedEvent event = PaymentRequestedEvent.of(payment, 1L);

            // act
            AopTestUtils.<PaymentEventHandler>getTargetObject(paymentEventHandler).handle(event);

            // assert
            PaymentModel updated = paymentJpaRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);

            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }
}
