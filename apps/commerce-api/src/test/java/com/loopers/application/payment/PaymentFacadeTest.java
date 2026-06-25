package com.loopers.application.payment;

import com.loopers.application.user.UserFacade;
import com.loopers.domain.order.Money;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentFacadeTest {

    private final UserFacade userFacade = mock(UserFacade.class);
    private final OrderService orderService = mock(OrderService.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PgClient pgClient = mock(PgClient.class);
    private final PaymentConfirmer paymentConfirmer = mock(PaymentConfirmer.class);
    private final PaymentFacade facade =
            new PaymentFacade(userFacade, orderService, paymentRepository, pgClient, paymentConfirmer);

    private static final String RAW_CARD = "1234-5678-9814-1451";

    private OrderModel pendingOrderOwnedBy(long userId, long amount) {
        // concrete 클래스 목은 doReturn/when 스타일(실제 메서드 미호출)로 스텁한다.
        OrderModel order = mock(OrderModel.class);
        Money money = mock(Money.class);
        doReturn(userId).when(order).getUserId();
        doReturn(OrderStatus.PENDING).when(order).getStatus();
        doReturn(amount).when(money).getAmount();
        doReturn(money).when(order).getFinalAmount();
        return order;
    }

    @BeforeEach
    void auth() {
        when(userFacade.authenticate("loginId", "pw")).thenReturn(1L);
    }

    @Test
    @DisplayName("정상 결제: PENDING 결제를 저장하고 PG에 원본 카드번호로 요청한 뒤 거래키를 반영한다")
    void given_pendingOrder_when_pay_then_savesPendingAndAssignsTxKey() {
        OrderModel order = pendingOrderOwnedBy(1L, 5000L);
        when(orderService.getOrder(10L)).thenReturn(order);
        when(paymentRepository.findByOrderId(10L)).thenReturn(List.of());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pgClient.requestPayment(any()))
                .thenReturn(new PgTransaction("20260622:TR:abc123", PaymentStatus.PENDING, null));

        PaymentInfo info = facade.pay("loginId", "pw", 10L, CardType.SAMSUNG, RAW_CARD);

        assertThat(info.transactionKey()).isEqualTo("20260622:TR:abc123");
        assertThat(info.status()).isEqualTo(PaymentStatus.PENDING);

        // PG로는 원본 카드번호 + 콜백 URL이 전달된다
        ArgumentCaptor<PgPaymentRequest> pg = ArgumentCaptor.forClass(PgPaymentRequest.class);
        verify(pgClient).requestPayment(pg.capture());
        assertThat(pg.getValue().cardNo()).isEqualTo(RAW_CARD);
        assertThat(pg.getValue().amount()).isEqualTo(5000L);
        assertThat(pg.getValue().callbackUrl()).startsWith("http://localhost:8080");

        // 영속되는 PaymentModel의 카드번호는 마스킹본
        ArgumentCaptor<PaymentModel> saved = ArgumentCaptor.forClass(PaymentModel.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getCardNo()).isEqualTo("1234-****-****-1451");
    }

    @Test
    @DisplayName("PG 요청 실패(서킷 OPEN/재시도 소진): PENDING 결제를 FAILED로 정리하고 예외를 전파한다(주문 미변경)")
    void given_pgUnavailable_when_pay_then_marksPaymentFailedAndPropagates() {
        OrderModel order = pendingOrderOwnedBy(1L, 5000L);
        when(orderService.getOrder(10L)).thenReturn(order);
        when(paymentRepository.findByOrderId(10L)).thenReturn(List.of());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pgClient.requestPayment(any()))
                .thenThrow(new CoreException(ErrorType.SERVICE_UNAVAILABLE, "PG 결제 요청을 처리할 수 없습니다."));

        assertThatThrownBy(() -> facade.pay("loginId", "pw", 10L, CardType.SAMSUNG, RAW_CARD))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.SERVICE_UNAVAILABLE);

        // PENDING 저장 후 FAILED로 정리되어 다시 저장된다(최소 2회) — 최종 상태는 FAILED, 거래키 없음
        ArgumentCaptor<PaymentModel> saved = ArgumentCaptor.forClass(PaymentModel.class);
        verify(paymentRepository, atLeast(2)).save(saved.capture());
        PaymentModel last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(last.getTransactionKey()).isNull();

        // 주문 상태는 건드리지 않아 재결제가 가능하다
        verify(orderService, never()).markPaid(any());
        verify(orderService, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("타인 주문 결제 시도는 NOT_FOUND")
    void given_othersOrder_when_pay_then_notFound() {
        OrderModel order = pendingOrderOwnedBy(999L, 5000L);
        when(orderService.getOrder(10L)).thenReturn(order);

        assertThatThrownBy(() -> facade.pay("loginId", "pw", 10L, CardType.KB, RAW_CARD))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.NOT_FOUND);
        verify(pgClient, never()).requestPayment(any());
    }

    @Test
    @DisplayName("PENDING이 아닌 주문은 CONFLICT")
    void given_nonPendingOrder_when_pay_then_conflict() {
        OrderModel order = mock(OrderModel.class);
        doReturn(1L).when(order).getUserId();
        doReturn(OrderStatus.PAID).when(order).getStatus();
        when(orderService.getOrder(10L)).thenReturn(order);

        assertThatThrownBy(() -> facade.pay("loginId", "pw", 10L, CardType.KB, RAW_CARD))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.CONFLICT);
        verify(pgClient, never()).requestPayment(any());
    }

    @Test
    @DisplayName("이미 진행 중(PENDING)인 결제가 있으면 멱등 가드로 CONFLICT")
    void given_activePayment_when_pay_then_conflict() {
        OrderModel order = pendingOrderOwnedBy(1L, 5000L);
        when(orderService.getOrder(10L)).thenReturn(order);
        PaymentModel pending = new PaymentModel(10L, 1L, CardType.SAMSUNG, RAW_CARD, 5000L); // PENDING
        when(paymentRepository.findByOrderId(10L)).thenReturn(List.of(pending));

        assertThatThrownBy(() -> facade.pay("loginId", "pw", 10L, CardType.SAMSUNG, RAW_CARD))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.CONFLICT);
        verify(pgClient, never()).requestPayment(any());
    }

    private PaymentModel pendingPaymentWithKey(long orderId, String transactionKey) {
        PaymentModel payment = new PaymentModel(orderId, 1L, CardType.SAMSUNG, RAW_CARD, 5000L); // PENDING
        payment.assignTransactionKey(transactionKey);
        return payment;
    }

    // ── §3.4 콜백: 확정 단위(PaymentConfirmer)로 위임 ──────────────────────────────
    // 실제 확정 로직(비관락·멱등·주문 cascade)은 PaymentConfirmerTest에서 검증한다. 여기서는 위임만 확인.

    @Test
    @DisplayName("콜백 처리: PaymentConfirmer.confirm에 위임하고 그 결과를 반환한다")
    void given_callback_when_handle_then_delegatesToConfirmer() {
        PaymentModel confirmed = pendingPaymentWithKey(10L, "20260623:TR:abc123");
        confirmed.markSuccess();
        when(paymentConfirmer.confirm("20260623:TR:abc123", PaymentStatus.SUCCESS, null)).thenReturn(confirmed);

        PaymentInfo info = facade.handleCallback("20260623:TR:abc123", PaymentStatus.SUCCESS, null);

        assertThat(info.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentConfirmer).confirm("20260623:TR:abc123", PaymentStatus.SUCCESS, null);
    }

    // ── §3.5 reconcile: PENDING 결제를 PG 진실원천으로 재확인해 확정 ───────────────────

    private PgTransaction pgTx(String key, PaymentStatus status, String reason) {
        return new PgTransaction(key, status, reason);
    }

    @Test
    @DisplayName("reconcile: PG가 SUCCESS면 confirm으로 확정하고 paid로 집계한다")
    void given_pendingPayment_when_reconcileWithPgSuccess_then_paid() {
        PaymentModel pending = pendingPaymentWithKey(10L, "TX-1");
        when(paymentRepository.findByStatus(PaymentStatus.PENDING, 0, 100)).thenReturn(List.of(pending));
        when(pgClient.findTransactionsByOrder(10L)).thenReturn(List.of(pgTx("TX-1", PaymentStatus.SUCCESS, null)));
        PaymentModel confirmed = pendingPaymentWithKey(10L, "TX-1");
        confirmed.markSuccess();
        when(paymentConfirmer.confirm("TX-1", PaymentStatus.SUCCESS, null)).thenReturn(confirmed);

        PaymentReconcileResult result = facade.reconcilePending(0, 100);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.paid()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(paymentConfirmer).confirm("TX-1", PaymentStatus.SUCCESS, null);
    }

    @Test
    @DisplayName("reconcile: PG가 FAILED면 confirm으로 실패 확정하고 failed로 집계한다")
    void given_pendingPayment_when_reconcileWithPgFailed_then_failed() {
        PaymentModel pending = pendingPaymentWithKey(10L, "TX-1");
        when(paymentRepository.findByStatus(PaymentStatus.PENDING, 0, 100)).thenReturn(List.of(pending));
        when(pgClient.findTransactionsByOrder(10L)).thenReturn(List.of(pgTx("TX-1", PaymentStatus.FAILED, "한도 초과")));
        PaymentModel confirmed = pendingPaymentWithKey(10L, "TX-1");
        confirmed.markFailed("한도 초과");
        when(paymentConfirmer.confirm("TX-1", PaymentStatus.FAILED, "한도 초과")).thenReturn(confirmed);

        PaymentReconcileResult result = facade.reconcilePending(0, 100);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.paid()).isZero();
    }

    @Test
    @DisplayName("reconcile: PG도 아직 PENDING이면 확정하지 않고 stillPending으로 미룬다")
    void given_pendingPayment_when_pgStillPending_then_stillPending() {
        PaymentModel pending = pendingPaymentWithKey(10L, "TX-1");
        when(paymentRepository.findByStatus(PaymentStatus.PENDING, 0, 100)).thenReturn(List.of(pending));
        when(pgClient.findTransactionsByOrder(10L)).thenReturn(List.of(pgTx("TX-1", PaymentStatus.PENDING, null)));

        PaymentReconcileResult result = facade.reconcilePending(0, 100);

        assertThat(result.stillPending()).isEqualTo(1);
        verify(paymentConfirmer, never()).confirm(any(), any(), any());
    }

    @Test
    @DisplayName("reconcile: PG에 해당 거래가 없으면 stillPending으로 미룬다")
    void given_pendingPayment_when_pgHasNoMatchingTx_then_stillPending() {
        PaymentModel pending = pendingPaymentWithKey(10L, "TX-1");
        when(paymentRepository.findByStatus(PaymentStatus.PENDING, 0, 100)).thenReturn(List.of(pending));
        when(pgClient.findTransactionsByOrder(10L)).thenReturn(List.of());

        PaymentReconcileResult result = facade.reconcilePending(0, 100);

        assertThat(result.stillPending()).isEqualTo(1);
        verify(paymentConfirmer, never()).confirm(any(), any(), any());
    }

    @Test
    @DisplayName("reconcile: 거래키 없는 고아 PENDING은 PG 조회 없이 skip 한다")
    void given_orphanPendingWithoutKey_when_reconcile_then_skipped() {
        PaymentModel orphan = new PaymentModel(10L, 1L, CardType.SAMSUNG, RAW_CARD, 5000L); // 거래키 미발급
        when(paymentRepository.findByStatus(PaymentStatus.PENDING, 0, 100)).thenReturn(List.of(orphan));

        PaymentReconcileResult result = facade.reconcilePending(0, 100);

        assertThat(result.skipped()).isEqualTo(1);
        verify(pgClient, never()).findTransactionsByOrder(any());
        verify(paymentConfirmer, never()).confirm(any(), any(), any());
    }

    @Test
    @DisplayName("reconcile: 확정 중 다른 경로가 먼저 확정해 CONFLICT면 skip으로 집계한다")
    void given_concurrentConfirm_when_reconcile_then_skipped() {
        PaymentModel pending = pendingPaymentWithKey(10L, "TX-1");
        when(paymentRepository.findByStatus(PaymentStatus.PENDING, 0, 100)).thenReturn(List.of(pending));
        when(pgClient.findTransactionsByOrder(10L)).thenReturn(List.of(pgTx("TX-1", PaymentStatus.SUCCESS, null)));
        when(paymentConfirmer.confirm("TX-1", PaymentStatus.SUCCESS, null))
                .thenThrow(new CoreException(ErrorType.CONFLICT, "이미 확정"));

        PaymentReconcileResult result = facade.reconcilePending(0, 100);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.paid()).isZero();
    }
}
