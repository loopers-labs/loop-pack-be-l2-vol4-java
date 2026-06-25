package com.loopers.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
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
}
