package com.loopers.payment.application;

import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayOrderTransactions;
import com.loopers.payment.domain.PaymentGatewayQueryResult;
import com.loopers.payment.domain.PaymentGatewayTransaction;
import com.loopers.payment.domain.PaymentGatewayTransactionDetail;
import com.loopers.payment.domain.PaymentService;
import com.loopers.payment.domain.PgPaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 1_351_039_135L;
    private static final long AMOUNT = 5_000L;
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String TRANSACTION_KEY = "20250816:TR:9577c5";
    private static final String OTHER_TRANSACTION_KEY = "20250816:TR:other";

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentRecoveryResultHandler resultHandler;

    private PaymentRecoveryFacade paymentRecoveryFacade;

    @BeforeEach
    void setUp() {
        paymentRecoveryFacade = new PaymentRecoveryFacade(
            paymentService,
            paymentGateway,
            resultHandler,
            new PaymentRecoveryProperties(100, 30, 15, 30, 120)
        );
    }

    @DisplayName("결제 복구를 실행할 때")
    @Nested
    class RecoverDuePayments {

        @DisplayName("PG 거래 키가 있으면 단건 조회 결과를 반영한다.")
        @Test
        void appliesTransactionDetail_whenPaymentHasTransactionKey() {
            // arrange
            Payment payment = createPendingPayment(ZonedDateTime.now().minusMinutes(1));
            PaymentGatewayTransactionDetail transaction = new PaymentGatewayTransactionDetail(
                TRANSACTION_KEY,
                ORDER_ID,
                CardType.SAMSUNG,
                AMOUNT,
                PgPaymentStatus.SUCCESS,
                "success"
            );
            when(paymentService.findRecoverablePayments(any(), any(), any(), eq(100)))
                .thenReturn(List.of(payment));
            when(paymentGateway.getTransaction(USER_ID, TRANSACTION_KEY))
                .thenReturn(PaymentGatewayQueryResult.found(transaction));

            // act
            paymentRecoveryFacade.recoverDuePayments();

            // assert
            verify(resultHandler).applyTransaction(eq(payment.getId()), eq(transaction), any(), any());
        }

        @DisplayName("PG 거래 키가 없고 주문 거래가 대기 중이면 거래 키를 기록하고 다음 확인을 예약한다.")
        @Test
        void marksPending_whenOrderTransactionIsPending() {
            // arrange
            Payment payment = createUnknownPayment(ZonedDateTime.now().minusMinutes(1));
            PaymentGatewayTransaction transaction = new PaymentGatewayTransaction(
                TRANSACTION_KEY,
                PgPaymentStatus.PENDING,
                "pending"
            );
            PaymentGatewayTransactionDetail transactionDetail = new PaymentGatewayTransactionDetail(
                TRANSACTION_KEY,
                ORDER_ID,
                CardType.SAMSUNG,
                AMOUNT,
                PgPaymentStatus.PENDING,
                "pending"
            );
            when(paymentService.findRecoverablePayments(any(), any(), any(), eq(100)))
                .thenReturn(List.of(payment));
            when(paymentGateway.getTransactionsByOrderId(USER_ID, ORDER_ID))
                .thenReturn(PaymentGatewayQueryResult.found(new PaymentGatewayOrderTransactions(
                    ORDER_ID,
                    List.of(transaction)
                )));
            when(paymentGateway.getTransaction(USER_ID, TRANSACTION_KEY))
                .thenReturn(PaymentGatewayQueryResult.found(transactionDetail));

            // act
            paymentRecoveryFacade.recoverDuePayments();

            // assert
            verify(resultHandler).applyTransaction(eq(payment.getId()), eq(transactionDetail), any(), any());
        }

        @DisplayName("주문에 여러 PG 거래가 있으면 현재 결제 정보와 일치하는 거래만 반영한다.")
        @Test
        void appliesMatchingTransaction_whenOrderHasMultipleTransactions() {
            // arrange
            Payment payment = createUnknownPayment(ZonedDateTime.now().minusMinutes(1));
            PaymentGatewayTransaction wrongTransaction = new PaymentGatewayTransaction(
                OTHER_TRANSACTION_KEY,
                PgPaymentStatus.SUCCESS,
                "success"
            );
            PaymentGatewayTransaction matchingTransaction = new PaymentGatewayTransaction(
                TRANSACTION_KEY,
                PgPaymentStatus.SUCCESS,
                "success"
            );
            PaymentGatewayTransactionDetail wrongDetail = new PaymentGatewayTransactionDetail(
                OTHER_TRANSACTION_KEY,
                ORDER_ID,
                CardType.HYUNDAI,
                AMOUNT + 100,
                PgPaymentStatus.SUCCESS,
                "success"
            );
            PaymentGatewayTransactionDetail matchingDetail = new PaymentGatewayTransactionDetail(
                TRANSACTION_KEY,
                ORDER_ID,
                CardType.SAMSUNG,
                AMOUNT,
                PgPaymentStatus.SUCCESS,
                "success"
            );
            when(paymentService.findRecoverablePayments(any(), any(), any(), eq(100)))
                .thenReturn(List.of(payment));
            when(paymentGateway.getTransactionsByOrderId(USER_ID, ORDER_ID))
                .thenReturn(PaymentGatewayQueryResult.found(new PaymentGatewayOrderTransactions(
                    ORDER_ID,
                    List.of(wrongTransaction, matchingTransaction)
                )));
            when(paymentGateway.getTransaction(USER_ID, OTHER_TRANSACTION_KEY))
                .thenReturn(PaymentGatewayQueryResult.found(wrongDetail));
            when(paymentGateway.getTransaction(USER_ID, TRANSACTION_KEY))
                .thenReturn(PaymentGatewayQueryResult.found(matchingDetail));

            // act
            paymentRecoveryFacade.recoverDuePayments();

            // assert
            verify(resultHandler).applyTransaction(eq(payment.getId()), eq(matchingDetail), any(), any());
        }

        @DisplayName("PG 거래를 찾지 못했지만 유예 시간이 남아있으면 다음 확인을 예약한다.")
        @Test
        void schedulesRecovery_whenNotFoundGracePeriodRemains() {
            // arrange
            Payment payment = createUnknownPayment(ZonedDateTime.now().minusSeconds(30));
            when(paymentService.findRecoverablePayments(any(), any(), any(), eq(100)))
                .thenReturn(List.of(payment));
            when(paymentGateway.getTransactionsByOrderId(USER_ID, ORDER_ID))
                .thenReturn(PaymentGatewayQueryResult.notFound("transaction not found"));

            // act
            paymentRecoveryFacade.recoverDuePayments();

            // assert
            verify(resultHandler).scheduleRecovery(eq(payment.getId()), eq("transaction not found"), any());
            verify(resultHandler, never()).markRequestFailed(any(), any(), any(), any());
        }

        @DisplayName("PG 거래를 찾지 못한 상태가 유예 시간을 넘기면 요청 실패로 확정한다.")
        @Test
        void marksRequestFailed_whenNotFoundGracePeriodExpires() {
            // arrange
            Payment payment = createUnknownPayment(ZonedDateTime.now().minusMinutes(3));
            when(paymentService.findRecoverablePayments(any(), any(), any(), eq(100)))
                .thenReturn(List.of(payment));
            when(paymentGateway.getTransactionsByOrderId(USER_ID, ORDER_ID))
                .thenReturn(PaymentGatewayQueryResult.notFound("transaction not found"));

            // act
            paymentRecoveryFacade.recoverDuePayments();

            // assert
            verify(resultHandler).markRequestFailed(
                eq(payment.getId()),
                eq(PaymentFailureReason.PG_TRANSACTION_NOT_FOUND),
                eq("transaction not found"),
                any()
            );
        }

        @DisplayName("PG 거래 키가 있는 결제의 거래를 찾지 못하면 유예 시간이 지나도 요청 실패로 확정하지 않는다.")
        @Test
        void schedulesRecovery_whenPendingPaymentTransactionIsNotFoundAfterGracePeriod() {
            // arrange
            Payment payment = createPendingPayment(ZonedDateTime.now().minusMinutes(3));
            when(paymentService.findRecoverablePayments(any(), any(), any(), eq(100)))
                .thenReturn(List.of(payment));
            when(paymentGateway.getTransaction(USER_ID, TRANSACTION_KEY))
                .thenReturn(PaymentGatewayQueryResult.notFound("transaction not found"));

            // act
            paymentRecoveryFacade.recoverDuePayments();

            // assert
            verify(resultHandler).scheduleRecovery(eq(payment.getId()), eq("transaction not found"), any());
            verify(resultHandler, never()).markRequestFailed(any(), any(), any(), any());
        }

        @DisplayName("한 결제 복구가 실패해도 다음 결제 복구는 계속 진행한다.")
        @Test
        void continuesRecovery_whenSinglePaymentRecoveryFails() {
            // arrange
            Payment failedPayment = createPendingPayment(ZonedDateTime.now().minusMinutes(1));
            Payment nextPayment = createUnknownPayment(ZonedDateTime.now().minusMinutes(1));
            PaymentGatewayTransaction transaction = new PaymentGatewayTransaction(
                TRANSACTION_KEY,
                PgPaymentStatus.PENDING,
                "pending"
            );
            when(paymentService.findRecoverablePayments(any(), any(), any(), eq(100)))
                .thenReturn(List.of(failedPayment, nextPayment));
            when(paymentGateway.getTransaction(USER_ID, TRANSACTION_KEY))
                .thenThrow(new RuntimeException("temporary failure"))
                .thenReturn(PaymentGatewayQueryResult.found(new PaymentGatewayTransactionDetail(
                    TRANSACTION_KEY,
                    ORDER_ID,
                    CardType.SAMSUNG,
                    AMOUNT,
                    PgPaymentStatus.PENDING,
                    "pending"
                )));
            when(paymentGateway.getTransactionsByOrderId(USER_ID, ORDER_ID))
                .thenReturn(PaymentGatewayQueryResult.found(new PaymentGatewayOrderTransactions(
                    ORDER_ID,
                    List.of(transaction)
                )));

            // act
            paymentRecoveryFacade.recoverDuePayments();

            // assert
            verify(resultHandler).applyTransaction(eq(nextPayment.getId()), any(), any(), any());
        }
    }

    private Payment createPendingPayment(ZonedDateTime requestedAt) {
        return Payment.pending(USER_ID, ORDER_ID, AMOUNT, CardType.SAMSUNG, CARD_NO, TRANSACTION_KEY, requestedAt);
    }

    private Payment createUnknownPayment(ZonedDateTime requestedAt) {
        return Payment.unknown(USER_ID, ORDER_ID, AMOUNT, CardType.SAMSUNG, CARD_NO, requestedAt);
    }
}
