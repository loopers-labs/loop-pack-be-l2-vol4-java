package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayTransaction;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentConfirmation paymentConfirmation;

    private PaymentRecovery paymentRecovery;

    @BeforeEach
    void setUp() {
        paymentRecovery = new PaymentRecovery(
            paymentRepository, paymentGateway, paymentConfirmation,
            new PaymentRecoveryProperties(Duration.ofSeconds(10), Duration.ofSeconds(30)));
    }

    @DisplayName("transactionKey 로 멈춘 PENDING 결제를 복구할 때, ")
    @Nested
    class RecoverStuckByKey {

        @DisplayName("PG 조회 결과가 SUCCESS 면, 그 transactionKey 로 SUCCESS 확정을 수렴시킨다.")
        @Test
        void confirmsSuccess_whenPgReportsSuccess() {
            // given
            given(paymentRepository.findStuckPending(any())).willReturn(List.of(keyedPending("key-1", 1L)));
            given(paymentGateway.getTransaction(1L, "key-1"))
                .willReturn(new PaymentGatewayTransaction("key-1", PaymentStatus.SUCCESS, "정상 승인"));

            // when
            paymentRecovery.recoverStuckByKey();

            // then
            verify(paymentGateway).getTransaction(1L, "key-1");
            verify(paymentConfirmation).confirm("key-1", PaymentStatus.SUCCESS, "정상 승인");
        }

        @DisplayName("PG 조회 결과가 FAILED 면, 그 transactionKey 로 FAILED 확정을 수렴시킨다.")
        @Test
        void confirmsFailed_whenPgReportsFailed() {
            // given
            given(paymentRepository.findStuckPending(any())).willReturn(List.of(keyedPending("key-1", 1L)));
            given(paymentGateway.getTransaction(1L, "key-1"))
                .willReturn(new PaymentGatewayTransaction("key-1", PaymentStatus.FAILED, "한도초과"));

            // when
            paymentRecovery.recoverStuckByKey();

            // then
            verify(paymentConfirmation).confirm("key-1", PaymentStatus.FAILED, "한도초과");
        }

        @DisplayName("PG 가 아직 PENDING 이면, 그대로 confirm 에 넘겨(미확정 no-op) 결과를 강제 단정하지 않는다.")
        @Test
        void routesPendingToConfirm_whenStillPending() {
            // given
            given(paymentRepository.findStuckPending(any())).willReturn(List.of(keyedPending("key-1", 1L)));
            given(paymentGateway.getTransaction(1L, "key-1"))
                .willReturn(new PaymentGatewayTransaction("key-1", PaymentStatus.PENDING, null));

            // when
            paymentRecovery.recoverStuckByKey();

            // then
            verify(paymentConfirmation).confirm("key-1", PaymentStatus.PENDING, null);
        }

        @DisplayName("멈춘 결제가 없으면, PG 조회도 확정도 하지 않는다.")
        @Test
        void doesNothing_whenNoStuckPayments() {
            // given
            given(paymentRepository.findStuckPending(any())).willReturn(List.of());

            // when
            paymentRecovery.recoverStuckByKey();

            // then
            verifyNoInteractions(paymentGateway, paymentConfirmation);
        }
    }

    @DisplayName("key 없이 멈춘 PENDING 결제를 복구할 때, ")
    @Nested
    class RecoverStuckByOrder {

        @DisplayName("PG 가 빈 목록(미접수)을 주면, 흡수할 key 없는 FAILED 로 confirmByOrder 에 위임한다.")
        @Test
        void confirmsFailedWithoutKey_whenPgReturnsEmpty() {
            // given
            given(paymentRepository.findStuckPendingWithoutKey(any())).willReturn(List.of(keylessPending(1L, 100L)));
            given(paymentGateway.getTransactionsByOrder(1L, 100L)).willReturn(List.of());

            // when
            paymentRecovery.recoverStuckByOrder();

            // then
            verify(paymentConfirmation).confirmByOrder(anyLong(),
                eq(new PaymentGatewayTransaction(null, PaymentStatus.FAILED, "PG 미접수")));
        }

        @DisplayName("PG 가 트랜잭션 목록을 주면, 대표 결과를 골라 confirmByOrder 에 위임한다.")
        @Test
        void confirmsResolvedOutcome_whenPgReturnsTransactions() {
            // given
            given(paymentRepository.findStuckPendingWithoutKey(any())).willReturn(List.of(keylessPending(1L, 100L)));
            given(paymentGateway.getTransactionsByOrder(1L, 100L)).willReturn(List.of(
                new PaymentGatewayTransaction("key-success", PaymentStatus.SUCCESS, "정상 승인")));

            // when
            paymentRecovery.recoverStuckByOrder();

            // then
            verify(paymentConfirmation).confirmByOrder(anyLong(),
                eq(new PaymentGatewayTransaction("key-success", PaymentStatus.SUCCESS, "정상 승인")));
        }

        @DisplayName("key 없이 멈춘 결제가 없으면, PG 조회도 확정도 하지 않는다.")
        @Test
        void doesNothing_whenNoKeylessStuck() {
            // given
            given(paymentRepository.findStuckPendingWithoutKey(any())).willReturn(List.of());

            // when
            paymentRecovery.recoverStuckByOrder();

            // then
            verifyNoInteractions(paymentGateway, paymentConfirmation);
        }

        private PaymentModel keylessPending(Long userId, Long orderId) {
            return PaymentModel.createPending(userId, orderId, 50_000L);
        }
    }

    private PaymentModel keyedPending(String transactionKey, Long userId) {
        PaymentModel payment = PaymentModel.createPending(userId, 100L, 50_000L);
        payment.assignTransactionKey(transactionKey);
        return payment;
    }
}
