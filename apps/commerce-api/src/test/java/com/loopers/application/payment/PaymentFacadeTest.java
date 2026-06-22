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
    private final PaymentFacade facade = new PaymentFacade(userFacade, orderService, paymentRepository, pgClient);

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
}
