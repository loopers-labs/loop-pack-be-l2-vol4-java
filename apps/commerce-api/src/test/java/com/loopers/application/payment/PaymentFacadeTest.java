package com.loopers.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final String CARD_NO = "1234-5678-9012-3456";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentFacade paymentFacade;

    private OrderModel order(int finalAmount) {
        return OrderModel.builder()
            .userId(USER_ID)
            .orderedAt(ZonedDateTime.now())
            .originalAmount(finalAmount)
            .discountAmount(0)
            .finalAmount(finalAmount)
            .build();
    }

    @DisplayName("결제를 요청할 때,")
    @Nested
    class CreatePayment {

        @DisplayName("주문이 없거나 본인 주문이 아니면 NOT_FOUND 예외가 발생하고 결제를 저장하지 않는다.")
        @Test
        void throwsNotFound_whenOrderNotOwned() {
            // arrange
            given(orderRepository.getActiveByIdAndUserId(ORDER_ID, USER_ID))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> paymentFacade.createPayment(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, ZonedDateTime.now()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);

            then(paymentRepository).should(never()).save(any());
            then(paymentGateway).shouldHaveNoInteractions();
        }

        @DisplayName("이미 결제가 존재하면 CONFLICT 예외가 발생하고 외부 결제 시스템을 호출하지 않는다.")
        @Test
        void throwsConflict_whenPaymentAlreadyExists() {
            // arrange
            given(orderRepository.getActiveByIdAndUserId(ORDER_ID, USER_ID)).willReturn(order(78_000));
            given(paymentRepository.existsByOrderId(ORDER_ID)).willReturn(true);

            // act & assert
            assertThatThrownBy(() -> paymentFacade.createPayment(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, ZonedDateTime.now()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);

            then(paymentRepository).should(never()).save(any());
            then(paymentGateway).shouldHaveNoInteractions();
        }

        @DisplayName("결제 금액은 요청이 아니라 대상 주문의 최종 결제 금액에서 도출한다.")
        @Test
        void derivesAmountFromOrder() {
            // arrange
            int finalAmount = 53_000;
            given(orderRepository.getActiveByIdAndUserId(ORDER_ID, USER_ID)).willReturn(order(finalAmount));
            given(paymentRepository.existsByOrderId(ORDER_ID)).willReturn(false);
            given(paymentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
            given(paymentGateway.requestPayment(any())).willReturn("TX-0001");

            // act
            PaymentInfo paymentInfo = paymentFacade.createPayment(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, ZonedDateTime.now());

            // assert
            ArgumentCaptor<PaymentModel> captor = ArgumentCaptor.forClass(PaymentModel.class);
            then(paymentRepository).should().save(captor.capture());
            assertAll(
                () -> assertThat(captor.getValue().getAmount()).isEqualTo(finalAmount),
                () -> assertThat(paymentInfo.amount()).isEqualTo(finalAmount)
            );
        }

        @DisplayName("PENDING으로 접수하고 외부 결제 시스템이 발급한 거래 식별자를 기록한다.")
        @Test
        void recordsTransactionKey_fromGateway() {
            // arrange
            given(orderRepository.getActiveByIdAndUserId(ORDER_ID, USER_ID)).willReturn(order(78_000));
            given(paymentRepository.existsByOrderId(ORDER_ID)).willReturn(false);
            given(paymentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
            given(paymentGateway.requestPayment(any())).willReturn("TX-0001");

            // act
            PaymentInfo paymentInfo = paymentFacade.createPayment(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, ZonedDateTime.now());

            // assert
            ArgumentCaptor<PaymentModel> paymentCaptor = ArgumentCaptor.forClass(PaymentModel.class);
            then(paymentGateway).should().requestPayment(paymentCaptor.capture());
            PaymentModel sentPayment = paymentCaptor.getValue();
            assertAll(
                () -> assertThat(paymentInfo.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(paymentInfo.transactionKey()).isEqualTo("TX-0001"),
                () -> assertThat(sentPayment.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(sentPayment.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(sentPayment.getAmount()).isEqualTo(78_000),
                () -> assertThat(sentPayment.getCardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(sentPayment.getCardNo().value()).isEqualTo(CARD_NO)
            );
        }
    }

    @DisplayName("결제 결과 콜백을 처리할 때,")
    @Nested
    class HandleCallback {

        private PaymentModel pendingPayment() {
            return PaymentModel.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .amount(78_000)
                .cardType(CardType.SAMSUNG)
                .rawCardNo(CARD_NO)
                .requestedAt(ZonedDateTime.now())
                .build();
        }

        @DisplayName("성공 콜백이면 결제를 SUCCESS로 확정하고 주문을 PAID로 전이한다.")
        @Test
        void confirmsSuccess_andMarksOrderPaid() {
            // arrange
            PaymentModel payment = pendingPayment();
            OrderModel order = order(78_000);
            given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment);
            given(orderRepository.getActiveById(ORDER_ID)).willReturn(order);

            // act
            paymentFacade.handleCallback(ORDER_ID, PaymentStatus.SUCCESS, null);

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("실패 콜백이면 결제를 FAILED로 확정하고 사유를 기록하며 주문을 PAYMENT_FAILED로 전이한다.")
        @Test
        void confirmsFailure_andMarksOrderPaymentFailed() {
            // arrange
            PaymentModel payment = pendingPayment();
            OrderModel order = order(78_000);
            given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment);
            given(orderRepository.getActiveById(ORDER_ID)).willReturn(order);

            // act
            paymentFacade.handleCallback(ORDER_ID, PaymentStatus.FAILED, "한도 초과");

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getReason()).isEqualTo("한도 초과"),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }

        @DisplayName("이미 확정된 결제면 상태를 바꾸지 않고 주문을 전이하지 않는다.")
        @Test
        void ignoresCallback_whenAlreadyTerminal() {
            // arrange
            PaymentModel payment = pendingPayment();
            payment.succeed();
            given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment);

            // act
            paymentFacade.handleCallback(ORDER_ID, PaymentStatus.FAILED, "한도 초과");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            then(orderRepository).should(never()).getActiveById(anyLong());
        }

        @DisplayName("예상치 못한 PENDING 콜백이면 결제·주문 상태를 바꾸지 않고 주문을 조회하지 않는다.")
        @Test
        void ignoresCallback_whenResultIsPending() {
            // arrange
            PaymentModel payment = pendingPayment();
            given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment);

            // act
            paymentFacade.handleCallback(ORDER_ID, PaymentStatus.PENDING, null);

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            then(orderRepository).should(never()).getActiveById(anyLong());
        }

        @DisplayName("콜백이 가리키는 결제가 없으면 NOT_FOUND 예외가 발생하고 주문을 전이하지 않는다.")
        @Test
        void throwsNotFound_whenPaymentAbsent() {
            // arrange
            given(paymentRepository.getByOrderId(ORDER_ID))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "결제가 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> paymentFacade.handleCallback(ORDER_ID, PaymentStatus.SUCCESS, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);

            then(orderRepository).should(never()).getActiveById(anyLong());
        }
    }
}
