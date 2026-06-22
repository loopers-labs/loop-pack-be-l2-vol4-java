package com.loopers.payment.application;

import com.loopers.order.domain.OrderItemModel;
import com.loopers.order.domain.OrderModel;
import com.loopers.order.domain.OrderStatus;
import com.loopers.order.infrastructure.OrderJpaRepository;
import com.loopers.payment.domain.PaymentModel;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.infrastructure.PaymentJpaRepository;
import com.loopers.payment.infrastructure.pg.PgPaymentClient;
import com.loopers.payment.infrastructure.pg.PgPaymentClientDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentFacadeIntegrationTest {

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private PgPaymentClient pgPaymentClient;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderModel savedOrder(Long userId) {
        return orderJpaRepository.save(
            new OrderModel(userId, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)))
        );
    }

    @DisplayName("결제를 요청할 때,")
    @Nested
    class RequestPayment {

        @DisplayName("정상 요청이면, Payment가 저장되고 Order 상태가 IN_PAYMENT로 변경된다.")
        @Test
        void savesPaymentAndChangesOrderToInPayment_whenValidRequest() {
            // arrange
            OrderModel order = savedOrder(1L);
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));

            // act
            PaymentInfo result = paymentFacade.requestPayment(1L, "user1", order.getId(), "SAMSUNG", "1234-5678-9012-3456");

            // assert
            PaymentModel savedPayment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(result.transactionKey()).isEqualTo("TX-001234"),
                () -> assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.IN_PAYMENT)
            );
        }

        @DisplayName("존재하지 않는 orderId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.requestPayment(1L, "user1", 999L, "SAMSUNG", "1234-5678-9012-3456")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("PENDING_PAYMENT가 아닌 주문이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIsNotPendingPayment() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.requestPayment(1L, "user1", order.getId(), "SAMSUNG", "1234-5678-9012-3456")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("PG 호출이 실패하면, 예외가 발생하고 Order 상태는 롤백된다.")
        @Test
        void throwsException_andRollbacksOrderStatus_whenPgCallFails() {
            // arrange
            OrderModel order = savedOrder(1L);
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenThrow(new CoreException(ErrorType.SERVICE_UNAVAILABLE, "PG 불가"));

            // act
            assertThrows(CoreException.class, () ->
                paymentFacade.requestPayment(1L, "user1", order.getId(), "SAMSUNG", "1234-5678-9012-3456")
            );

            // assert
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }
    }

    @DisplayName("콜백을 처리할 때,")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백이면, Payment가 SUCCESS, Order가 CONFIRMED로 변경된다.")
        @Test
        void confirmsPaymentAndOrder_whenSuccessCallback() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L));

            // act
            paymentFacade.handleCallback("TX-001234", "SUCCESS");

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED)
            );
        }

        @DisplayName("FAILED 콜백이면, Payment가 FAILED, Order가 PAYMENT_FAILED로 변경된다.")
        @Test
        void failsPaymentAndOrder_whenFailedCallback() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L));

            // act
            paymentFacade.handleCallback("TX-001234", "FAILED");

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }

        @DisplayName("이미 처리된 transactionKey로 콜백이 재수신되면, 무시된다.")
        @Test
        void ignores_whenCallbackAlreadyProcessed() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            PaymentModel payment = paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L));
            payment.confirm();
            paymentJpaRepository.save(payment);

            // act (예외 없이 실행)
            paymentFacade.handleCallback("TX-001234", "SUCCESS");

            // assert
            PaymentModel savedPayment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("존재하지 않는 transactionKey로 콜백이 오면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTransactionKeyNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.handleCallback("UNKNOWN-KEY", "SUCCESS")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
