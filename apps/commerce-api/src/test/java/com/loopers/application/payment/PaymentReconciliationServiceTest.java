package com.loopers.application.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentRequestResult;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    private static final Long PAYMENT_ID = 10L;
    private static final Long ORDER_ID = 100L;
    private static final String TX_KEY = "TX-0001";
    private static final String CARD_NO = "1234-5678-9012-3456";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentTransactionWriter paymentTransactionWriter;

    @InjectMocks
    private PaymentReconciliationService paymentReconciliationService;

    private PaymentModel pendingPayment(ZonedDateTime requestedAt, String transactionKey) {
        PaymentModel payment = PaymentModel.builder()
            .orderId(ORDER_ID)
            .userId(1L)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo(CARD_NO)
            .requestedAt(requestedAt)
            .build();
        if (transactionKey != null) {
            payment.recordTransactionKey(transactionKey);
        }
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    @DisplayName("미해결 결제를 보정할 때,")
    @Nested
    class Reconcile {

        @DisplayName("외부 결제 시스템에서 종료 상태로 확인되면 그 결과로 확정한다.")
        @Test
        void confirms_whenGatewayResolved() {
            // arrange
            PaymentModel payment = pendingPayment(ZonedDateTime.now(), TX_KEY);
            PaymentTransactionStatus resolved = PaymentTransactionStatus.found(TX_KEY, PaymentStatus.SUCCESS, null);
            given(paymentRepository.getById(PAYMENT_ID)).willReturn(payment);
            given(paymentGateway.queryTransaction(payment)).willReturn(resolved);

            // act
            paymentReconciliationService.reconcile(PAYMENT_ID, ZonedDateTime.now());

            // assert
            then(paymentTransactionWriter).should().confirm(payment, resolved);
        }

        @DisplayName("외부 결제 시스템이 아직 처리 중이고 격리 상한 안이면 다음 주기로 미룬다.")
        @Test
        void defers_whenStillProcessing_withinThreshold() {
            // arrange
            PaymentModel payment = pendingPayment(ZonedDateTime.now(), TX_KEY);
            given(paymentRepository.getById(PAYMENT_ID)).willReturn(payment);
            given(paymentGateway.queryTransaction(payment))
                .willReturn(PaymentTransactionStatus.found(TX_KEY, PaymentStatus.PENDING, null));

            // act
            paymentReconciliationService.reconcile(PAYMENT_ID, ZonedDateTime.now());

            // assert
            then(paymentTransactionWriter).shouldHaveNoInteractions();
        }

        @DisplayName("결과 불명이 격리 상한을 넘으면 STUCK으로 격리한다.")
        @Test
        void isolates_whenUnknown_overThreshold() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            PaymentModel payment = pendingPayment(now.minusMinutes(11), TX_KEY);
            given(paymentRepository.getById(PAYMENT_ID)).willReturn(payment);
            given(paymentGateway.queryTransaction(payment)).willReturn(PaymentTransactionStatus.unknown());

            // act
            paymentReconciliationService.reconcile(PAYMENT_ID, now);

            // assert
            then(paymentTransactionWriter).should().isolate(payment);
        }

        @DisplayName("외부 결제 시스템에 거래가 없으면(미도달) 재요청한다.")
        @Test
        void reRequests_whenNotReached() {
            // arrange
            PaymentModel payment = pendingPayment(ZonedDateTime.now(), null);
            PaymentRequestResult requestResult = PaymentRequestResult.accepted(TX_KEY);
            given(paymentRepository.getById(PAYMENT_ID)).willReturn(payment);
            given(paymentGateway.queryTransaction(payment)).willReturn(PaymentTransactionStatus.notFound());
            given(paymentGateway.requestPayment(payment)).willReturn(requestResult);

            // act
            paymentReconciliationService.reconcile(PAYMENT_ID, ZonedDateTime.now());

            // assert
            then(paymentTransactionWriter).should().reapplyRequest(payment, requestResult);
        }

        @DisplayName("이미 종료된 결제면 외부 조회 없이 아무 것도 하지 않는다.")
        @Test
        void skips_whenAlreadyTerminal() {
            // arrange
            PaymentModel payment = pendingPayment(ZonedDateTime.now(), TX_KEY);
            payment.applyRequestResult(PaymentRequestResult.rejected("이미 실패"));
            given(paymentRepository.getById(PAYMENT_ID)).willReturn(payment);

            // act
            paymentReconciliationService.reconcile(PAYMENT_ID, ZonedDateTime.now());

            // assert
            then(paymentGateway).should(never()).queryTransaction(any());
            then(paymentTransactionWriter).shouldHaveNoInteractions();
        }
    }
}
