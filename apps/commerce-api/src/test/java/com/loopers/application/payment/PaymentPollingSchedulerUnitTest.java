package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.pg.PgApiResponse;
import com.loopers.infrastructure.pg.PgFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PaymentPollingSchedulerUnitTest {

    @Mock private PaymentService paymentService;
    @Mock private PgFeignClient pgFeignClient;
    @Mock private OrderService orderService;

    private PaymentPollingScheduler scheduler;

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final String TRANSACTION_KEY = "20260624:TR:abc123";
    private static final int MAX_POLLING_COUNT = 5;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentPollingScheduler(paymentService, pgFeignClient, orderService);
    }

    @DisplayName("결제 폴링 스케줄러가")
    @Nested
    class PollPendingPayments {

        @DisplayName("처리 대상 결제가 없으면, 폴링을 수행하지 않는다.")
        @Test
        void doesNothing_whenNoPaymentsFound() {
            // Arrange
            given(paymentService.findAllPendingOrInProgress()).willReturn(List.of());

            // Act
            scheduler.pollPendingPayments();

            // Assert
            then(paymentService).should(never()).recordPolling(any());
        }

        @DisplayName("CREATED 결제에서 PG에 거래 내역이 없고 폴링 횟수가 최대치 미만이면, 폴링만 기록한다.")
        @Test
        void recordsPolling_whenCreatedPaymentHasNoTransactionOnPgAndBelowMax() {
            // Arrange
            Payment pendingPayment = pendingPayment(0);
            Payment recordedPayment = pendingPayment(1);
            given(paymentService.findAllPendingOrInProgress()).willReturn(List.of(pendingPayment));
            given(pgFeignClient.getPaymentStatusByOrderId(String.valueOf(USER_ID), String.format("%06d", ORDER_ID)))
                .willReturn(new PgApiResponse.PaymentStatusWithOrderId(String.format("%06d", ORDER_ID), List.of()));
            given(paymentService.recordPolling(pendingPayment)).willReturn(recordedPayment);

            // Act
            scheduler.pollPendingPayments();

            // Assert
            then(paymentService).should().recordPolling(pendingPayment);
            then(paymentService).should(never()).exhaustPolling(any());
        }

        @DisplayName("CREATED 결제에서 PG에 거래 내역이 없고 폴링 횟수가 최대치에 도달하면, POLLING_EXHAUSTED 처리한다.")
        @Test
        void exhaustsPolling_whenCreatedPaymentHasNoTransactionOnPgAndReachesMax() {
            // Arrange
            Payment pendingPayment = pendingPayment(MAX_POLLING_COUNT - 1);
            Payment recordedPayment = pendingPayment(MAX_POLLING_COUNT);
            given(paymentService.findAllPendingOrInProgress()).willReturn(List.of(pendingPayment));
            given(pgFeignClient.getPaymentStatusByOrderId(String.valueOf(USER_ID), String.format("%06d", ORDER_ID)))
                .willReturn(new PgApiResponse.PaymentStatusWithOrderId(String.format("%06d", ORDER_ID), List.of()));
            given(paymentService.recordPolling(pendingPayment)).willReturn(recordedPayment);

            // Act
            scheduler.pollPendingPayments();

            // Assert
            then(paymentService).should().exhaustPolling(recordedPayment);
        }

        @DisplayName("IN_PROGRESS 결제에서 PG가 SUCCESS를 반환하면, 폴링 기록 없이 결제를 완료하고 주문을 확정한다.")
        @Test
        void completesSuccessAndConfirmsOrder_whenInProgressAndPgReturnsSuccess() {
            // Arrange
            Payment inProgressPayment = inProgressPayment(0);
            given(paymentService.findAllPendingOrInProgress()).willReturn(List.of(inProgressPayment));
            given(pgFeignClient.getPaymentStatus(String.valueOf(USER_ID), TRANSACTION_KEY)).willReturn(
                new PgApiResponse.PaymentStatus(TRANSACTION_KEY, String.format("%06d", ORDER_ID), "SAMSUNG", "1234-5678-9012-3456", 50000L, "SUCCESS", "정상 승인되었습니다.")
            );

            // Act
            scheduler.pollPendingPayments();

            // Assert
            then(paymentService).should(never()).recordPolling(any());
            then(paymentService).should().complete(TRANSACTION_KEY, PaymentStatus.SUCCESS, "정상 승인되었습니다.");
            then(orderService).should().confirm(ORDER_ID);
        }

        @DisplayName("IN_PROGRESS 결제에서 PG가 FAILED를 반환하면, 폴링 기록 없이 결제만 실패 처리하고 주문은 변경하지 않는다.")
        @Test
        void completesFailed_andDoesNotConfirmOrder_whenInProgressAndPgReturnsFailed() {
            // Arrange
            Payment inProgressPayment = inProgressPayment(0);
            given(paymentService.findAllPendingOrInProgress()).willReturn(List.of(inProgressPayment));
            given(pgFeignClient.getPaymentStatus(String.valueOf(USER_ID), TRANSACTION_KEY)).willReturn(
                new PgApiResponse.PaymentStatus(TRANSACTION_KEY, String.format("%06d", ORDER_ID), "SAMSUNG", "1234-5678-9012-3456", 50000L, "FAILED", "한도초과입니다.")
            );

            // Act
            scheduler.pollPendingPayments();

            // Assert
            then(paymentService).should(never()).recordPolling(any());
            then(paymentService).should().complete(TRANSACTION_KEY, PaymentStatus.FAILED, "한도초과입니다.");
            then(orderService).should(never()).confirm(any());
        }

        @DisplayName("IN_PROGRESS 결제에서 PG가 미결 상태를 반환하고 폴링 횟수가 최대치 미만이면, 폴링만 기록한다.")
        @Test
        void recordsPolling_whenInProgressAndPgReturnsNonTerminalStatusAndBelowMax() {
            // Arrange
            Payment inProgressPayment = inProgressPayment(0);
            Payment recordedPayment = inProgressPayment(1);
            given(paymentService.findAllPendingOrInProgress()).willReturn(List.of(inProgressPayment));
            given(pgFeignClient.getPaymentStatus(String.valueOf(USER_ID), TRANSACTION_KEY)).willReturn(
                new PgApiResponse.PaymentStatus(TRANSACTION_KEY, String.format("%06d", ORDER_ID), "SAMSUNG", "1234-5678-9012-3456", 50000L, "IN_PROGRESS", null)
            );
            given(paymentService.recordPolling(inProgressPayment)).willReturn(recordedPayment);

            // Act
            scheduler.pollPendingPayments();

            // Assert
            then(paymentService).should().recordPolling(inProgressPayment);
            then(paymentService).should(never()).exhaustPolling(any());
            then(paymentService).should(never()).complete(any(), any(), any());
        }

        @DisplayName("IN_PROGRESS 결제에서 PG가 미결 상태를 반환하고 폴링 횟수가 최대치에 도달하면, POLLING_EXHAUSTED 처리한다.")
        @Test
        void exhaustsPolling_whenInProgressAndPgReturnsNonTerminalStatusAndReachesMax() {
            // Arrange
            Payment inProgressPayment = inProgressPayment(MAX_POLLING_COUNT - 1);
            Payment recordedPayment = inProgressPayment(MAX_POLLING_COUNT);
            given(paymentService.findAllPendingOrInProgress()).willReturn(List.of(inProgressPayment));
            given(pgFeignClient.getPaymentStatus(String.valueOf(USER_ID), TRANSACTION_KEY)).willReturn(
                new PgApiResponse.PaymentStatus(TRANSACTION_KEY, String.format("%06d", ORDER_ID), "SAMSUNG", "1234-5678-9012-3456", 50000L, "IN_PROGRESS", null)
            );
            given(paymentService.recordPolling(inProgressPayment)).willReturn(recordedPayment);

            // Act
            scheduler.pollPendingPayments();

            // Assert
            then(paymentService).should().exhaustPolling(recordedPayment);
        }

        @DisplayName("PG 상태 조회 중 예외가 발생하면, 폴링 횟수를 증가시키지 않고 다음 결제 처리를 계속한다.")
        @Test
        void doesNotRecordPolling_andContinuesProcessing_whenPgQueryThrowsException() {
            // Arrange
            Payment inProgressPayment = inProgressPayment(0);
            given(paymentService.findAllPendingOrInProgress()).willReturn(List.of(inProgressPayment));
            given(pgFeignClient.getPaymentStatus(anyString(), anyString())).willThrow(new RuntimeException("PG unavailable"));

            // Act (예외가 전파되지 않아야 함)
            scheduler.pollPendingPayments();

            // Assert
            then(paymentService).should(never()).recordPolling(any());
            then(paymentService).should(never()).complete(any(), any(), any());
        }
    }

    private Payment pendingPayment(int pollingCount) {
        return new Payment(1L, USER_ID, ORDER_ID, null, CardType.SAMSUNG, "1234-5678-9012-3456",
            50000L, PaymentStatus.CREATED, null, pollingCount, null, null, null, null, null);
    }

    private Payment inProgressPayment(int pollingCount) {
        return new Payment(1L, USER_ID, ORDER_ID, TRANSACTION_KEY, CardType.SAMSUNG, "1234-5678-9012-3456",
            50000L, PaymentStatus.IN_PROGRESS, null, pollingCount, null, null, null, null, null);
    }
}