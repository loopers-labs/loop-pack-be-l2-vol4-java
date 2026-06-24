package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayLookup;
import com.loopers.domain.payment.GatewayStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentService paymentService;

    private PaymentRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new PaymentRecoveryService(paymentRepository, paymentGateway, paymentService, Duration.ofMinutes(5));
    }

    private PaymentModel payment(String transactionKey) {
        PaymentModel payment = new PaymentModel(1L, 10L, CardType.SAMSUNG, Money.of(5_000L));
        if (transactionKey != null) {
            payment.assignTransactionKey(transactionKey);
        }
        return payment;
    }

    @DisplayName("거래키 있는 PENDING 복구 시")
    @Nested
    class ReconcilePending {

        @DisplayName("PG 조회 결과가 SUCCESS이면 성공으로 확정한다")
        @Test
        void confirmsSuccess() {
            when(paymentRepository.findAllByStatus(PaymentStatus.PENDING)).thenReturn(List.of(payment("tx-1")));
            when(paymentGateway.queryStatus("tx-1", 10L)).thenReturn(Optional.of(new GatewayStatus("SUCCESS", null)));

            recoveryService.reconcilePending();

            verify(paymentService).confirmFromGatewayStatus("tx-1", "SUCCESS", null);
        }

        @DisplayName("PG가 응답하지 않으면(empty) 확정하지 않고 다음 주기로 미룬다")
        @Test
        void skipsWhenGatewayUnavailable() {
            when(paymentRepository.findAllByStatus(PaymentStatus.PENDING)).thenReturn(List.of(payment("tx-1")));
            when(paymentGateway.queryStatus("tx-1", 10L)).thenReturn(Optional.empty());

            recoveryService.reconcilePending();

            verify(paymentService, never()).confirmFromGatewayStatus(any(), any(), any());
        }
    }

    @DisplayName("거래키 없는 PENDING 복구(recoverKeyless) 시")
    @Nested
    class RecoverKeyless {

        @DisplayName("PG에 거래가 있으면 거래키를 backfill하고 결과대로 확정한다")
        @Test
        void backfillsAndConfirms_whenFound() {
            when(paymentRepository.findKeylessPendingBefore(any())).thenReturn(List.of(payment(null)));
            when(paymentGateway.queryByOrderId(1L, 10L)).thenReturn(GatewayLookup.found("tx-2", "SUCCESS", null));

            recoveryService.recoverKeyless();

            verify(paymentService).assignTransactionKey(1L, "tx-2");
            verify(paymentService).confirmFromGatewayStatus("tx-2", "SUCCESS", null);
        }

        @DisplayName("PG에 거래가 없으면(NOT_FOUND) 미접수로 보고 실패 처리(취소)한다")
        @Test
        void failsWhenNotFound() {
            when(paymentRepository.findKeylessPendingBefore(any())).thenReturn(List.of(payment(null)));
            when(paymentGateway.queryByOrderId(1L, 10L)).thenReturn(GatewayLookup.notFound());

            recoveryService.recoverKeyless();

            verify(paymentService).failByOrderId(eq(1L), any());
            verify(paymentService, never()).confirmFromGatewayStatus(any(), any(), any());
        }

        @DisplayName("PG 장애(UNREACHABLE)면 취소하지 않고 다음 주기로 미룬다")
        @Test
        void skipsWhenUnreachable() {
            when(paymentRepository.findKeylessPendingBefore(any())).thenReturn(List.of(payment(null)));
            when(paymentGateway.queryByOrderId(1L, 10L)).thenReturn(GatewayLookup.unreachable());

            recoveryService.recoverKeyless();

            verify(paymentService, never()).failByOrderId(any(), any());
            verify(paymentService, never()).confirmFromGatewayStatus(any(), any(), any());
            verify(paymentService, never()).assignTransactionKey(any(), any());
        }
    }
}
