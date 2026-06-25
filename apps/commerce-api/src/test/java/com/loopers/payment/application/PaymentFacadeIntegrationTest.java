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
import static org.mockito.ArgumentMatchers.eq;
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
            new OrderModel(userId, "user1", List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)), null, 0L)
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

        @DisplayName("PG 호출이 실패하면, 예외가 발생하고 Order 상태가 PAYMENT_FAILED로 전이된다.")
        @Test
        void throwsException_andSetsOrderToPaymentFailed_whenPgCallFails() {
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
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }

        @DisplayName("PAYMENT_FAILED 주문이면, 재결제가 정상적으로 진행된다.")
        @Test
        void savesPaymentAndChangesOrderToInPayment_whenOrderIsPaymentFailed() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            order.failPayment();
            orderJpaRepository.save(order);
            when(pgPaymentClient.requestPayment(anyString(), any()))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001235", "PENDING", null));

            // act
            PaymentInfo result = paymentFacade.requestPayment(1L, "user1", order.getId(), "SAMSUNG", "1234-5678-9012-3456");

            // assert
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(result.transactionKey()).isEqualTo("TX-001235"),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.IN_PAYMENT)
            );
        }
    }

    @DisplayName("콜백을 처리할 때,")
    @Nested
    class HandleCallback {

        @DisplayName("PG 재조회 결과가 SUCCESS이면, Payment가 SUCCESS, Order가 CONFIRMED로 변경된다.")
        @Test
        void confirmsPaymentAndOrder_whenPgReturnsSuccess() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L, "user1"));
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "SUCCESS", "정상 승인되었습니다."));

            // act
            paymentFacade.handleCallback("TX-001234", order.getId());

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED)
            );
        }

        @DisplayName("PG 재조회 결과가 FAILED이면, Payment가 FAILED, Order가 PAYMENT_FAILED로 변경된다.")
        @Test
        void failsPaymentAndOrder_whenPgReturnsFailed() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L, "user1"));
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "FAILED", "한도초과입니다."));

            // act
            paymentFacade.handleCallback("TX-001234", order.getId());

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }

        @DisplayName("PG 재조회 결과가 아직 PENDING이면, 상태 변경 없이 무시된다.")
        @Test
        void doesNothing_whenPgReturnsPending() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L, "user1"));
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));

            // act
            paymentFacade.handleCallback("TX-001234", order.getId());

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.IN_PAYMENT)
            );
        }

        @DisplayName("이미 처리된 transactionKey로 콜백이 재수신되면, PG 재조회 없이 무시된다.")
        @Test
        void ignores_whenCallbackAlreadyProcessed() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            PaymentModel payment = paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L, "user1"));
            payment.confirm();
            paymentJpaRepository.save(payment);

            // act (예외 없이 실행)
            paymentFacade.handleCallback("TX-001234", order.getId());

            // assert
            PaymentModel savedPayment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("orderId의 주문이 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.handleCallback("UNKNOWN-KEY", 999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("로컬에 PaymentModel이 없고 PG가 SUCCESS 반환하면, PaymentModel이 새로 생성되고 Order가 CONFIRMED로 변경된다.")
        @Test
        void createsPaymentAndConfirmsOrder_whenNoLocalPaymentButPgReturnsSuccess() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "SUCCESS", "정상 승인되었습니다."));

            // act
            paymentFacade.handleCallback("TX-001234", order.getId());

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED)
            );
        }

        @DisplayName("로컬에 PaymentModel이 없고 PG에도 거래 기록이 없으면, 무시된다.")
        @Test
        void doesNothing_whenNoLocalPaymentAndPgHasNoRecord() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            when(pgPaymentClient.getTransaction(anyString(), eq("UNKNOWN-KEY")))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "결제건이 존재하지 않습니다."));

            // act (예외 없이 실행)
            paymentFacade.handleCallback("UNKNOWN-KEY", order.getId());

            // assert
            assertThat(paymentJpaRepository.findByTransactionKey("UNKNOWN-KEY")).isEmpty();
        }
    }

    @DisplayName("결제 상태를 복구할 때,")
    @Nested
    class RecoverPayment {

        @DisplayName("PENDING Payment가 있고 PG가 SUCCESS 반환하면, Payment SUCCESS, Order CONFIRMED로 변경된다.")
        @Test
        void confirmsPaymentAndOrder_whenPgReturnsSuccess() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L, "user1"));
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "SUCCESS", "정상 승인되었습니다."));

            // act
            paymentFacade.recoverPayment(order.getId(), 1L, "user1");

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED)
            );
        }

        @DisplayName("PENDING Payment가 있고 PG가 FAILED 반환하면, Payment FAILED, Order PAYMENT_FAILED로 변경된다.")
        @Test
        void failsPaymentAndOrder_whenPgReturnsFailed() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L, "user1"));
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "FAILED", "한도초과입니다."));

            // act
            paymentFacade.recoverPayment(order.getId(), 1L, "user1");

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }

        @DisplayName("PENDING Payment가 있고 PG가 PENDING 반환하면, 상태 변경 없이 무시된다.")
        @Test
        void doesNothing_whenPgReturnsPending() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L, "user1"));
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-001234")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-001234", "PENDING", null));

            // act
            paymentFacade.recoverPayment(order.getId(), 1L, "user1");

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.IN_PAYMENT)
            );
        }

        @DisplayName("존재하지 않는 orderId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.recoverPayment(999L, 1L, "user1")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("주문 소유자가 아니면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenNotOrderOwner() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L, "user1"));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.recoverPayment(order.getId(), 2L, "user2")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("로컬에 Payment가 없고 PG에도 거래 기록이 없으면, 변경 없이 종료된다.")
        @Test
        void doesNothing_whenNoLocalPaymentAndPgHasNoRecord() {
            // arrange
            OrderModel order = savedOrder(1L);
            when(pgPaymentClient.getTransactionsByOrder(anyString(), eq(order.getId().toString())))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "결제건이 존재하지 않습니다."));

            // act (예외 없이 실행)
            paymentFacade.recoverPayment(order.getId(), 1L, "user1");

            // assert
            assertThat(paymentJpaRepository.findFirstByOrderIdOrderByIdDesc(order.getId())).isEmpty();
        }

        @DisplayName("로컬에 Payment가 없지만 PG에 거래가 있고 SUCCESS이면, Payment가 새로 생성되고 Order가 CONFIRMED로 변경된다.")
        @Test
        void createsPaymentAndConfirmsOrder_whenNoLocalPaymentButPgHasSuccess() {
            // arrange
            OrderModel order = savedOrder(1L);
            when(pgPaymentClient.getTransactionsByOrder(anyString(), eq(order.getId().toString())))
                .thenReturn(new PgPaymentClientDto.OrderTransactionsResponse(
                    order.getId().toString(),
                    List.of(new PgPaymentClientDto.TransactionResponse("TX-009999", "SUCCESS", "정상 승인되었습니다."))
                ));
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-009999")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-009999", "SUCCESS", "정상 승인되었습니다."));

            // act
            paymentFacade.recoverPayment(order.getId(), 1L, "user1");

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-009999").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED)
            );
        }

        @DisplayName("로컬에 Payment가 없지만 PG에 거래가 있고 FAILED이면, Payment가 새로 생성되고 Order가 PAYMENT_FAILED로 변경된다.")
        @Test
        void createsPaymentAndFailsOrder_whenNoLocalPaymentButPgHasFailed() {
            // arrange
            OrderModel order = savedOrder(1L);
            when(pgPaymentClient.getTransactionsByOrder(anyString(), eq(order.getId().toString())))
                .thenReturn(new PgPaymentClientDto.OrderTransactionsResponse(
                    order.getId().toString(),
                    List.of(new PgPaymentClientDto.TransactionResponse("TX-009999", "FAILED", "한도초과입니다."))
                ));
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-009999")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-009999", "FAILED", "한도초과입니다."));

            // act
            paymentFacade.recoverPayment(order.getId(), 1L, "user1");

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-009999").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }

        @DisplayName("로컬에 Payment가 없지만 PG에 거래가 있고 아직 PENDING이면, Payment는 PENDING으로 생성되고 Order는 변경되지 않는다.")
        @Test
        void createsPendingPayment_whenNoLocalPaymentButPgHasPending() {
            // arrange
            OrderModel order = savedOrder(1L);
            when(pgPaymentClient.getTransactionsByOrder(anyString(), eq(order.getId().toString())))
                .thenReturn(new PgPaymentClientDto.OrderTransactionsResponse(
                    order.getId().toString(),
                    List.of(new PgPaymentClientDto.TransactionResponse("TX-009999", "PENDING", null))
                ));
            when(pgPaymentClient.getTransaction(anyString(), eq("TX-009999")))
                .thenReturn(new PgPaymentClientDto.TransactionResponse("TX-009999", "PENDING", null));

            // act
            paymentFacade.recoverPayment(order.getId(), 1L, "user1");

            // assert
            PaymentModel payment = paymentJpaRepository.findByTransactionKey("TX-009999").orElseThrow();
            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT)
            );
        }

        @DisplayName("이미 처리된 Payment이면, 변경 없이 무시된다.")
        @Test
        void doesNothing_whenPaymentAlreadyProcessed() {
            // arrange
            OrderModel order = savedOrder(1L);
            order.startPayment();
            orderJpaRepository.save(order);
            PaymentModel payment = paymentJpaRepository.save(new PaymentModel(order.getId(), "TX-001234", "SAMSUNG", 150000L, "user1"));
            payment.confirm();
            paymentJpaRepository.save(payment);

            // act (예외 없이 실행)
            paymentFacade.recoverPayment(order.getId(), 1L, "user1");

            // assert
            assertThat(paymentJpaRepository.findByTransactionKey("TX-001234").orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
        }
    }
}
