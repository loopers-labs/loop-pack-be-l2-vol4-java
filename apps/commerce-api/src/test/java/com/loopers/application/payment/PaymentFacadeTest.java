package com.loopers.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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
import com.loopers.domain.payment.PaymentRequestResult;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentTransactionStatus;
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

    @Mock
    private PaymentTransactionWriter paymentTransactionWriter;

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
            given(paymentGateway.requestPayment(any())).willReturn(PaymentRequestResult.accepted("TX-0001"));

            // act
            PaymentInfo paymentInfo = paymentFacade.createPayment(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, ZonedDateTime.now());

            // assert
            ArgumentCaptor<PaymentModel> captor = ArgumentCaptor.forClass(PaymentModel.class);
            then(paymentRepository).should(times(2)).save(captor.capture());
            assertAll(
                () -> assertThat(captor.getAllValues().get(0).getAmount()).isEqualTo(finalAmount),
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
            given(paymentGateway.requestPayment(any())).willReturn(PaymentRequestResult.accepted("TX-0001"));

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

        @DisplayName("결과가 불명이면 거래 식별자 없이 PENDING으로 접수만 남기고 정상 응답한다.")
        @Test
        void keepsPending_whenResultUnknown() {
            // arrange
            given(orderRepository.getActiveByIdAndUserId(ORDER_ID, USER_ID)).willReturn(order(78_000));
            given(paymentRepository.existsByOrderId(ORDER_ID)).willReturn(false);
            given(paymentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
            given(paymentGateway.requestPayment(any())).willReturn(PaymentRequestResult.unknown());

            // act
            PaymentInfo paymentInfo = paymentFacade.createPayment(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, ZonedDateTime.now());

            // assert
            assertAll(
                () -> assertThat(paymentInfo.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(paymentInfo.transactionKey()).isNull()
            );
        }

        @DisplayName("접수가 거절되면 FAILED로 확정하고 정상 응답한다.")
        @Test
        void marksFailed_whenResultRejected() {
            // arrange
            given(orderRepository.getActiveByIdAndUserId(ORDER_ID, USER_ID)).willReturn(order(78_000));
            given(paymentRepository.existsByOrderId(ORDER_ID)).willReturn(false);
            given(paymentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
            given(paymentGateway.requestPayment(any())).willReturn(PaymentRequestResult.rejected("결제가 거절되었습니다."));

            // act
            PaymentInfo paymentInfo = paymentFacade.createPayment(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, ZonedDateTime.now());

            // assert
            assertThat(paymentInfo.status()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    @DisplayName("결제 결과 콜백을 처리할 때,")
    @Nested
    class HandleCallback {

        private static final String TX_KEY = "TX-0001";

        private PaymentModel pendingPayment() {
            PaymentModel payment = PaymentModel.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .amount(78_000)
                .cardType(CardType.SAMSUNG)
                .rawCardNo(CARD_NO)
                .requestedAt(ZonedDateTime.now())
                .build();
            payment.recordTransactionKey(TX_KEY);
            return payment;
        }

        @DisplayName("콜백을 받으면 콜백 내용이 아니라 외부 결제 시스템 조회 결과로 결제를 확정한다.")
        @Test
        void confirmsWithGatewayResult_notCallbackPayload() {
            // arrange
            PaymentModel payment = pendingPayment();
            given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment);
            PaymentTransactionStatus verified = PaymentTransactionStatus.found(TX_KEY, PaymentStatus.SUCCESS, null);
            given(paymentGateway.queryTransaction(payment)).willReturn(verified);

            // act
            paymentFacade.handleCallback(ORDER_ID, TX_KEY);

            // assert
            then(paymentTransactionWriter).should().confirm(payment, verified);
        }

        @DisplayName("외부 결제 시스템이 아직 처리 중이면 확정하지 않고 폴링 보정에 맡긴다.")
        @Test
        void defersToReconciliation_whenGatewayStillProcessing() {
            // arrange
            PaymentModel payment = pendingPayment();
            given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment);
            given(paymentGateway.queryTransaction(payment))
                .willReturn(PaymentTransactionStatus.found(TX_KEY, PaymentStatus.PENDING, null));

            // act
            paymentFacade.handleCallback(ORDER_ID, TX_KEY);

            // assert
            then(paymentTransactionWriter).shouldHaveNoInteractions();
        }

        @DisplayName("외부 결제 시스템 조회가 결과 불명이면 확정하지 않고 폴링 보정에 맡긴다.")
        @Test
        void defersToReconciliation_whenGatewayUnknown() {
            // arrange
            PaymentModel payment = pendingPayment();
            given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment);
            given(paymentGateway.queryTransaction(payment)).willReturn(PaymentTransactionStatus.unknown());

            // act
            paymentFacade.handleCallback(ORDER_ID, TX_KEY);

            // assert
            then(paymentTransactionWriter).shouldHaveNoInteractions();
        }

        @DisplayName("콜백의 거래 식별자가 결제와 일치하지 않으면 FORBIDDEN 예외가 발생하고 외부 조회·확정을 하지 않는다.")
        @Test
        void throwsForbidden_whenTransactionKeyMismatch() {
            // arrange
            PaymentModel payment = pendingPayment();
            given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment);

            // act & assert
            assertThatThrownBy(() -> paymentFacade.handleCallback(ORDER_ID, "TX-FORGED"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.FORBIDDEN);

            then(paymentGateway).should(never()).queryTransaction(any());
            then(paymentTransactionWriter).shouldHaveNoInteractions();
        }

        @DisplayName("이미 확정된 결제면 외부 조회·확정을 하지 않는다.")
        @Test
        void ignoresCallback_whenAlreadyTerminal() {
            // arrange
            PaymentModel payment = pendingPayment();
            payment.applyRequestResult(PaymentRequestResult.rejected("이미 실패"));
            given(paymentRepository.getByOrderId(ORDER_ID)).willReturn(payment);

            // act
            paymentFacade.handleCallback(ORDER_ID, TX_KEY);

            // assert
            then(paymentGateway).should(never()).queryTransaction(any());
            then(paymentTransactionWriter).shouldHaveNoInteractions();
        }

        @DisplayName("콜백이 가리키는 결제가 없으면 NOT_FOUND 예외가 발생하고 외부 조회·확정을 하지 않는다.")
        @Test
        void throwsNotFound_whenPaymentAbsent() {
            // arrange
            given(paymentRepository.getByOrderId(ORDER_ID))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "결제가 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> paymentFacade.handleCallback(ORDER_ID, TX_KEY))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);

            then(paymentGateway).should(never()).queryTransaction(any());
            then(paymentTransactionWriter).shouldHaveNoInteractions();
        }
    }
}
