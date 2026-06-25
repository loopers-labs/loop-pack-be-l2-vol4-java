package com.loopers.payment.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItems;
import com.loopers.order.domain.OrderService;
import com.loopers.order.domain.OrderStatus;
import com.loopers.order.domain.vo.OrderAmountSnapshot;
import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentGatewayTransaction;
import com.loopers.payment.domain.PaymentService;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgPaymentStatus;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ORDER_ID = 1_351_039_135L;
    private static final long AMOUNT = 1_550_000L;
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String TRANSACTION_KEY = "20250816:TR:9577c5";

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentFacade paymentFacade;

    @DisplayName("결제를 요청할 때")
    @Nested
    class RequestPayment {

        @DisplayName("PG 요청이 접수되면, 결제 대기 상태로 저장한다.")
        @Test
        void savesPendingPayment_whenPgRequestIsAccepted() {
            // arrange
            Order order = createOrder();
            RequestPaymentCommand command = createCommand();
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);
            when(paymentService.findLatestPaymentByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentGateway.requestPayment(argThat(request -> request.amount() == AMOUNT)))
                .thenReturn(PaymentGatewayResult.accepted(new PaymentGatewayTransaction(
                    TRANSACTION_KEY,
                    PgPaymentStatus.PENDING,
                    null
                )));
            when(paymentService.savePayment(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            PaymentInfo result = paymentFacade.requestPayment(command);

            // assert
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentService).savePayment(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getValue();

            assertAll(
                () -> assertThat(result.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(result.pgTransactionKey()).isEqualTo(TRANSACTION_KEY),
                () -> assertThat(savedPayment.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(savedPayment.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(savedPayment.getAmount()).isEqualTo(AMOUNT),
                () -> assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(savedPayment.getPgStatus()).isEqualTo(PgPaymentStatus.PENDING),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING)
            );
        }

        @DisplayName("PG 요청이 실패하면, 결제 요청 실패 상태로 저장한다.")
        @Test
        void savesRequestFailedPayment_whenPgRequestFails() {
            // arrange
            Order order = createOrder();
            RequestPaymentCommand command = createCommand();
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);
            when(paymentService.findLatestPaymentByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentGateway.requestPayment(any()))
                .thenReturn(PaymentGatewayResult.failed(PaymentFailureReason.PG_UNAVAILABLE, "connect refused"));
            when(paymentService.savePayment(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            PaymentInfo result = paymentFacade.requestPayment(command);

            // assert
            assertAll(
                () -> assertThat(result.status()).isEqualTo(PaymentStatus.REQUEST_FAILED),
                () -> assertThat(result.failureReason()).isEqualTo(PaymentFailureReason.PG_UNAVAILABLE),
                () -> assertThat(result.pgTransactionKey()).isNull(),
                () -> assertThat(result.completedAt()).isNotNull(),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING)
            );
        }

        @DisplayName("PG 요청 결과를 알 수 없으면, 결제 확인 필요 상태로 저장한다.")
        @Test
        void savesUnknownPayment_whenPgRequestIsUnknown() {
            // arrange
            Order order = createOrder();
            RequestPaymentCommand command = createCommand();
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);
            when(paymentService.findLatestPaymentByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentGateway.requestPayment(any()))
                .thenReturn(PaymentGatewayResult.unknown(PaymentFailureReason.PG_TIMEOUT, "read timed out"));
            when(paymentService.savePayment(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            PaymentInfo result = paymentFacade.requestPayment(command);

            // assert
            assertAll(
                () -> assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN),
                () -> assertThat(result.failureReason()).isEqualTo(PaymentFailureReason.PG_TIMEOUT),
                () -> assertThat(result.pgTransactionKey()).isNull(),
                () -> assertThat(result.completedAt()).isNull(),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING)
            );
        }

        @DisplayName("다른 사용자의 주문이면, 결제를 요청할 수 없다.")
        @Test
        void throwsForbidden_whenOrderBelongsToOtherUser() {
            // arrange
            Order order = createOrder();
            RequestPaymentCommand command = new RequestPaymentCommand(OTHER_USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO);
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);

            // act & assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.FORBIDDEN);
            verifyNoInteractions(paymentGateway);
            verify(paymentService, never()).savePayment(any());
        }

        @DisplayName("이미 결제 확인 중인 결제가 있으면, 새 결제를 요청할 수 없다.")
        @Test
        void throwsConflict_whenPaymentIsAlreadyInProgress() {
            // arrange
            Order order = createOrder();
            RequestPaymentCommand command = createCommand();
            Payment payment = Payment.pending(
                USER_ID,
                ORDER_ID,
                AMOUNT,
                CardType.SAMSUNG,
                CARD_NO,
                TRANSACTION_KEY,
                ZonedDateTime.now()
            );
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);
            when(paymentService.findLatestPaymentByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

            // act & assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);
            verifyNoInteractions(paymentGateway);
            verify(paymentService, never()).savePayment(any());
        }

        @DisplayName("결제 금액이 0원이면, PG 결제를 요청할 수 없다.")
        @Test
        void throwsConflict_whenPaymentAmountIsZero() {
            // arrange
            Order order = createFreeOrder();
            RequestPaymentCommand command = createCommand();
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);

            // act & assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);
            verifyNoInteractions(paymentGateway);
            verify(paymentService, never()).savePayment(any());
        }
    }

    private RequestPaymentCommand createCommand() {
        return new RequestPaymentCommand(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO);
    }

    private Order createOrder() {
        OrderItem item = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", AMOUNT, 1);
        return Order.create(USER_ID, OrderItems.of(List.of(item)));
    }

    private Order createFreeOrder() {
        OrderItem item = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", AMOUNT, 1);
        OrderAmountSnapshot amountSnapshot = OrderAmountSnapshot.withDiscount(AMOUNT, AMOUNT);
        return Order.create(USER_ID, OrderItems.of(List.of(item)), 10L, amountSnapshot);
    }
}
