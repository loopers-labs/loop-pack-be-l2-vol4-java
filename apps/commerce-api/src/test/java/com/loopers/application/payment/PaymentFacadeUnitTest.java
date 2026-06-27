package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.mockito.Mockito;
import com.loopers.infrastructure.pg.PgApiResponse;
import com.loopers.infrastructure.pg.PgFeignClient;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeUnitTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PgFeignClient pgFeignClient;

    private PaymentFacade paymentFacade;

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    @BeforeEach
    void setUp() {
        paymentFacade = new PaymentFacade(orderService, paymentService, pgFeignClient, CALLBACK_URL);
    }

    @DisplayName("결제를 요청할 때,")
    @Nested
    class RequestPayment {

        @DisplayName("주문이 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            // Arrange
            PaymentCommand.Request command = new PaymentCommand.Request(USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456");
            given(orderService.getOrder(ORDER_ID)).willThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));

            // Act & Assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("타 유저의 주문이면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOrderBelongsToAnotherUser() {
            // Arrange
            PaymentCommand.Request command = new PaymentCommand.Request(USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456");
            Order order = pendingOrder(USER_ID + 1); // 다른 유저의 주문
            given(orderService.getOrder(ORDER_ID)).willReturn(order);

            // Act & Assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }

        @DisplayName("주문 상태가 PENDING이 아니면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenOrderStatusIsNotPending() {
            // Arrange
            PaymentCommand.Request command = new PaymentCommand.Request(USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456");
            Order order = orderWithStatus(USER_ID, OrderStatus.CONFIRMED);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);

            // Act & Assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("이미 SUCCESS 결제가 존재하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenSuccessPaymentAlreadyExists() {
            // Arrange
            PaymentCommand.Request command = new PaymentCommand.Request(USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456");
            given(orderService.getOrder(ORDER_ID)).willReturn(pendingOrder(USER_ID));
            given(paymentService.hasSuccessPayment(ORDER_ID)).willReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("PG 호출 성공 시 Payment를 IN_PROGRESS로 전환하고 transactionKey를 반환한다.")
        @Test
        void returnsTransactionKey_andMarksPaymentInProgress_whenPgSucceeds() {
            // Arrange
            PaymentCommand.Request command = new PaymentCommand.Request(USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456");
            Order order = pendingOrder(USER_ID);
            Payment payment = pendingPayment();
            String transactionKey = "20260623:TR:abc123";

            given(orderService.getOrder(ORDER_ID)).willReturn(order);
            given(paymentService.hasSuccessPayment(ORDER_ID)).willReturn(false);
            given(paymentService.createPayment(anyLong(), anyLong(), any(), anyString(), anyLong())).willReturn(payment);
            given(pgFeignClient.requestPayment(anyString(), any()))
                .willReturn(new PgApiResponse.Payment(transactionKey, "PENDING", null));

            // Act
            PaymentInfo.Create result = paymentFacade.requestPayment(command);

            // Assert
            assertThat(result.transactionKey()).isEqualTo(transactionKey);
            then(paymentService).should().inProgress(payment, transactionKey);
        }

        @DisplayName("PG 호출 최종 실패 시 SERVICE_UNAVAILABLE 예외가 발생한다.")
        @Test
        void throwsServiceUnavailable_whenPgFails() {
            // Arrange
            PaymentCommand.Request command = new PaymentCommand.Request(USER_ID, ORDER_ID, CardType.SAMSUNG, "1234-5678-9012-3456");
            given(orderService.getOrder(ORDER_ID)).willReturn(pendingOrder(USER_ID));
            given(paymentService.hasSuccessPayment(ORDER_ID)).willReturn(false);
            given(paymentService.createPayment(anyLong(), anyLong(), any(), anyString(), anyLong())).willReturn(pendingPayment());
            given(pgFeignClient.requestPayment(anyString(), any()))
                .willThrow(FeignException.InternalServerError.class);

            // Act & Assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.SERVICE_UNAVAILABLE));
        }
    }

    @DisplayName("콜백을 수신할 때,")
    @Nested
    class ReceiveCallback {

        private static final String TRANSACTION_KEY = "20260623:TR:abc123";

        @DisplayName("SUCCESS 콜백이면 결제를 SUCCESS로 완료하고 주문을 확정한다.")
        @Test
        void completesSuccessAndConfirmsOrder_whenCallbackIsSuccess() {
            Payment inProgressPayment = inProgressPayment();
            given(paymentService.getByTransactionKey(TRANSACTION_KEY)).willReturn(inProgressPayment);

            paymentFacade.receiveCallback(new PaymentCommand.Callback(TRANSACTION_KEY, PaymentStatus.SUCCESS, "정상 승인되었습니다."));

            then(paymentService).should().complete(TRANSACTION_KEY, PaymentStatus.SUCCESS, "정상 승인되었습니다.");
            then(orderService).should().confirm(ORDER_ID);
        }

        @DisplayName("FAILED 콜백이면 결제만 FAILED로 완료하고 주문은 건드리지 않는다.")
        @Test
        void completesFailedOnly_whenCallbackIsFailed() {
            Payment inProgressPayment = inProgressPayment();
            given(paymentService.getByTransactionKey(TRANSACTION_KEY)).willReturn(inProgressPayment);

            paymentFacade.receiveCallback(new PaymentCommand.Callback(TRANSACTION_KEY, PaymentStatus.FAILED, "한도초과입니다."));

            then(paymentService).should().complete(TRANSACTION_KEY, PaymentStatus.FAILED, "한도초과입니다.");
            then(orderService).should(Mockito.never()).confirm(ORDER_ID);
        }

        @DisplayName("이미 SUCCESS 상태의 결제면 아무것도 하지 않는다.")
        @Test
        void doesNothing_whenPaymentAlreadySuccess() {
            Payment successPayment = completedPayment(PaymentStatus.SUCCESS);
            given(paymentService.getByTransactionKey(TRANSACTION_KEY)).willReturn(successPayment);

            paymentFacade.receiveCallback(new PaymentCommand.Callback(TRANSACTION_KEY, PaymentStatus.SUCCESS, "정상 승인되었습니다."));

            then(paymentService).should(Mockito.never()).complete(any(), any(), any());
            then(orderService).should(Mockito.never()).confirm(any());
        }

        @DisplayName("이미 FAILED 상태의 결제면 아무것도 하지 않는다.")
        @Test
        void doesNothing_whenPaymentAlreadyFailed() {
            Payment failedPayment = completedPayment(PaymentStatus.FAILED);
            given(paymentService.getByTransactionKey(TRANSACTION_KEY)).willReturn(failedPayment);

            paymentFacade.receiveCallback(new PaymentCommand.Callback(TRANSACTION_KEY, PaymentStatus.FAILED, "한도초과입니다."));

            then(paymentService).should(Mockito.never()).complete(any(), any(), any());
            then(orderService).should(Mockito.never()).confirm(any());
        }

        @DisplayName("존재하지 않는 transactionKey면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTransactionKeyDoesNotExist() {
            given(paymentService.getByTransactionKey(TRANSACTION_KEY))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다."));

            assertThatThrownBy(() -> paymentFacade.receiveCallback(
                new PaymentCommand.Callback(TRANSACTION_KEY, PaymentStatus.SUCCESS, "정상 승인되었습니다.")))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    private Order pendingOrder(Long userId) {
        return orderWithStatus(userId, OrderStatus.PENDING);
    }

    private Order orderWithStatus(Long userId, OrderStatus status) {
        return new Order(ORDER_ID, userId, null, status,
            BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.valueOf(50000),
            null, null, null);
    }

    private Payment pendingPayment() {
        return new Payment(1L, USER_ID, ORDER_ID, null, CardType.SAMSUNG, "1234-5678-9012-3456",
            50000L, PaymentStatus.CREATED, null, 0, null, null, null, null, null);
    }

    private Payment inProgressPayment() {
        return new Payment(1L, USER_ID, ORDER_ID, "20260623:TR:abc123", CardType.SAMSUNG, "1234-5678-9012-3456",
            50000L, PaymentStatus.IN_PROGRESS, null, 0, null, null, null, null, null);
    }

    private Payment completedPayment(PaymentStatus status) {
        return new Payment(1L, USER_ID, ORDER_ID, "20260623:TR:abc123", CardType.SAMSUNG, "1234-5678-9012-3456",
            50000L, status, null, 0, null, null, null, null, null);
    }
}
