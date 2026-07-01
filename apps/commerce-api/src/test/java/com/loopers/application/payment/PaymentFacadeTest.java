package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.pg.PgCallbackPayload;
import com.loopers.infrastructure.pg.PgPaymentClient;
import com.loopers.infrastructure.pg.PgPaymentResult;
import com.loopers.infrastructure.pg.PgTransactionResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @InjectMocks private PaymentFacade paymentFacade;

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PgPaymentClient pgPaymentClient;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private CircuitBreaker pgPaymentCircuitBreaker;

    private static final Long USER_ID  = 1L;
    private static final Long ORDER_ID  = 10L;
    private static final Long PAYMENT_ID = 100L;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReflectionTestUtils.setField(paymentFacade, "callbackUrl", "http://test/callback");

        // TransactionTemplate: 콜백을 인라인으로 실행 (실제 트랜잭션 없음)
        given(transactionTemplate.execute(any())).willAnswer(inv -> {
            TransactionCallback<Object> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        doAnswer(inv -> {
            Consumer<TransactionStatus> consumer = inv.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // CircuitBreaker: 공급자를 그대로 실행 (CB 동작은 별도 검증 불필요)
        given(pgPaymentCircuitBreaker.run(any(), any())).willAnswer(inv -> {
            java.util.function.Supplier<Object> supplier = inv.getArgument(0);
            return supplier.get();
        });
    }

    private OrderModel createOrder() {
        OrderModel order = new OrderModel(USER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "totalAmount", 10_000);
        return order;
    }

    private PaymentModel createPaymentWithStatus(PaymentStatus status) {
        PaymentModel payment = new PaymentModel(ORDER_ID, USER_ID, CardType.SAMSUNG, "1234-5678-9014-1451", 10_000);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        ReflectionTestUtils.setField(payment, "status", status);
        if (status == PaymentStatus.IN_PROGRESS) {
            ReflectionTestUtils.setField(payment, "pgTransactionId", "TX-EXISTING");
        }
        return payment;
    }

    @Nested
    @DisplayName("requestPayment() 호출 시,")
    class RequestPayment {

        private final PaymentCommand command =
            new PaymentCommand(ORDER_ID, USER_ID, CardType.SAMSUNG, "1234-5678-9014-1451");

        @Test
        @DisplayName("유효한 요청에서 PG 성공 시 IN_PROGRESS 상태의 PaymentInfo가 반환된다.")
        void returnsInProgressPayment_whenPgSucceeds() {
            // arrange
            OrderModel order = createOrder();
            PaymentModel pendingPayment = createPaymentWithStatus(PaymentStatus.PENDING);

            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
            given(paymentRepository.save(any())).willReturn(pendingPayment);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(pendingPayment));
            given(pgPaymentClient.request(any())).willReturn(new PgPaymentResult("TX-123"));

            // act
            PaymentInfo result = paymentFacade.requestPayment(command);

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.IN_PROGRESS);
            assertThat(result.pgTransactionId()).isEqualTo("TX-123");
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID이면 NOT_FOUND 예외가 발생한다.")
        void throwsNotFound_whenOrderDoesNotExist() {
            // arrange
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.requestPayment(command));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(paymentRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("타인의 주문이면 NOT_FOUND 예외가 발생한다.")
        void throwsNotFound_whenOrderBelongsToOtherUser() {
            // arrange
            OrderModel otherOrder = new OrderModel(999L); // 다른 유저
            ReflectionTestUtils.setField(otherOrder, "id", ORDER_ID);
            ReflectionTestUtils.setField(otherOrder, "totalAmount", 10_000);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(otherOrder));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.requestPayment(command));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("주문 상태가 PENDING이 아니면 BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenOrderStatusIsNotPending() {
            // arrange
            OrderModel order = createOrder();
            ReflectionTestUtils.setField(order, "status", OrderStatus.CONFIRMED);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.requestPayment(command));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("이미 IN_PROGRESS 결제가 존재하면 CONFLICT 예외가 발생한다.")
        void throwsConflict_whenInProgressPaymentExists() {
            // arrange
            OrderModel order = createOrder();
            PaymentModel existing = createPaymentWithStatus(PaymentStatus.IN_PROGRESS);

            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(existing));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.requestPayment(command));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @Test
        @DisplayName("PG 호출 실패 시 결제가 ABORTED로 처리되고 BAD_REQUEST 예외가 발생한다.")
        void abortsPaymentAndThrowsBadRequest_whenPgFails() {
            // arrange
            OrderModel order = createOrder();
            PaymentModel pendingPayment = createPaymentWithStatus(PaymentStatus.PENDING);

            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
            given(paymentRepository.save(any())).willReturn(pendingPayment);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(pendingPayment));
            given(pgPaymentClient.request(any()))
                .willThrow(new CoreException(ErrorType.BAD_REQUEST, "PG 오류"));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.requestPayment(command));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.ABORTED);
        }
    }

    @Nested
    @DisplayName("handleCallback() 호출 시,")
    class HandleCallback {

        @Test
        @DisplayName("SUCCESS 콜백 수신 시 결제가 SUCCESS로, 주문이 CONFIRMED로 전환된다.")
        void marksSuccessAndConfirmsOrder_whenCallbackIsSuccess() {
            // arrange
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.IN_PROGRESS);
            OrderModel order = createOrder();

            given(paymentRepository.findByPgTransactionId("TX-EXISTING")).willReturn(Optional.of(payment));
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

            // act
            paymentFacade.handleCallback(new PgCallbackPayload("TX-EXISTING", null, "SUCCESS", null));

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("FAILED 콜백 수신 시 결제가 FAILED로 전환되고 failureCode가 저장된다.")
        void marksFailedWithCode_whenCallbackIsFailed() {
            // arrange
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.IN_PROGRESS);
            given(paymentRepository.findByPgTransactionId("TX-EXISTING")).willReturn(Optional.of(payment));

            // act
            paymentFacade.handleCallback(new PgCallbackPayload("TX-EXISTING", null, "FAILED", "LIMIT_EXCEEDED"));

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureCode()).isEqualTo("LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("존재하지 않는 transactionKey이면 NOT_FOUND 예외가 발생한다.")
        void throwsNotFound_whenTransactionKeyNotFound() {
            // arrange
            given(paymentRepository.findByPgTransactionId(anyString())).willReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.handleCallback(new PgCallbackPayload("TX-UNKNOWN", null, "SUCCESS", null)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("syncPayment() 호출 시,")
    class SyncPayment {

        @Test
        @DisplayName("이미 SUCCESS 상태이면 PG 조회 없이 현재 상태를 반환한다.")
        void returnsCurrentStatus_whenAlreadySuccess() {
            // arrange
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.SUCCESS);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

            // act
            PaymentInfo result = paymentFacade.syncPayment(PAYMENT_ID, USER_ID);

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            then(pgPaymentClient).should(never()).getStatus(anyString(), any());
            then(pgPaymentClient).should(never()).findByOrderId(anyString(), any());
        }

        @Test
        @DisplayName("IN_PROGRESS 상태에서 PG SUCCESS 응답 수신 시 SUCCESS로 전환되고 주문이 확정된다.")
        void transitionsToSuccess_whenInProgressAndPgSucceeds() {
            // arrange
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.IN_PROGRESS);
            OrderModel order = createOrder();

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willReturn(payment);
            given(pgPaymentClient.getStatus("TX-EXISTING", USER_ID))
                .willReturn(Optional.of(new PgTransactionResponse("TX-EXISTING", "SUCCESS", null)));
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

            // act
            PaymentInfo result = paymentFacade.syncPayment(PAYMENT_ID, USER_ID);

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("ABORTED 상태에서 PG SUCCESS 응답 수신 시 SUCCESS로 복구된다.")
        void recoversToSuccess_whenAbortedAndPgSucceeds() {
            // arrange
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.ABORTED);
            OrderModel order = createOrder();
            String paddedOrderId = String.format("%010d", ORDER_ID);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willReturn(payment);
            given(pgPaymentClient.findByOrderId(paddedOrderId, USER_ID))
                .willReturn(Optional.of(new PgTransactionResponse("TX-RECOVERED", "SUCCESS", null)));
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

            // act
            PaymentInfo result = paymentFacade.syncPayment(PAYMENT_ID, USER_ID);

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(result.pgTransactionId()).isEqualTo("TX-RECOVERED");
        }

        @Test
        @DisplayName("PG에 기록이 없으면 현재 상태를 유지한다.")
        void maintainsCurrentStatus_whenPgHasNoRecord() {
            // arrange
            PaymentModel payment = createPaymentWithStatus(PaymentStatus.ABORTED);
            String paddedOrderId = String.format("%010d", ORDER_ID);

            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(paymentRepository.save(any())).willReturn(payment);
            given(pgPaymentClient.findByOrderId(paddedOrderId, USER_ID)).willReturn(Optional.empty());

            // act
            PaymentInfo result = paymentFacade.syncPayment(PAYMENT_ID, USER_ID);

            // assert
            assertThat(result.status()).isEqualTo(PaymentStatus.ABORTED);
        }

        @Test
        @DisplayName("타인의 결제 조회 시 NOT_FOUND 예외가 발생한다.")
        void throwsNotFound_whenPaymentBelongsToOtherUser() {
            // arrange
            PaymentModel payment = new PaymentModel(ORDER_ID, 999L, CardType.SAMSUNG, "1234", 10_000);
            ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.syncPayment(PAYMENT_ID, USER_ID));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
