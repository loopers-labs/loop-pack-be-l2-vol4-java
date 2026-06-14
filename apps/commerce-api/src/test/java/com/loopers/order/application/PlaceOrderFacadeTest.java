package com.loopers.order.application;

import com.loopers.common.domain.Money;
import com.loopers.order.domain.OrderStatus;
import com.loopers.payment.application.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceOrderFacadeTest {

    private static final Long ORDER_ID = 100L;
    private static final String ORDER_NUMBER = "20260528-000001";

    private final PlaceOrderService placeOrderService = mock(PlaceOrderService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final OrderCompensationService orderCompensationService = mock(OrderCompensationService.class);
    private final OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);

    private final PlaceOrderFacade placeOrderFacade =
            new PlaceOrderFacade(placeOrderService, paymentService, orderCompensationService, orderNumberGenerator);

    private OrderCommand.Create command() {
        return new OrderCommand.Create(
                1L, List.of(new OrderCommand.Line(10L, 2)),
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동", 50L
        );
    }

    private OrderResult.Detail pendingOrder() {
        return new OrderResult.Detail(
                ORDER_ID, "20260528-000001", OrderStatus.PENDING,
                58_000L, 3_000L, 55_000L, 50L,
                ZonedDateTime.now(),
                new OrderResult.Recipient("김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동"),
                List.of()
        );
    }

    @Test
    @DisplayName("결제 성공: 주문 생성 후 최종 금액으로 결제하고 보상하지 않는다")
    void givenPaymentSucceeds_whenPlace_thenPaysFinalAmountAndDoesNotCompensate() {
        when(orderNumberGenerator.generate()).thenReturn(ORDER_NUMBER);
        when(placeOrderService.createPendingOrder(any(), eq(ORDER_NUMBER))).thenReturn(pendingOrder());

        OrderResult.Detail result = placeOrderFacade.place(command());

        ArgumentCaptor<Money> payCaptor = ArgumentCaptor.forClass(Money.class);
        verify(paymentService).pay(eq(ORDER_ID), payCaptor.capture());
        assertAll(
                () -> assertThat(result.orderId()).isEqualTo(ORDER_ID),
                () -> assertThat(payCaptor.getValue().value()).isEqualTo(55_000L)
        );
        verify(orderCompensationService, never()).compensate(any());
    }

    @Test
    @DisplayName("주문번호는 주문 트랜잭션(createPendingOrder) 보다 먼저 채번되어 전달된다")
    void givenPlace_whenInvoked_thenGeneratesOrderNumberBeforeCreatingOrder() {
        when(orderNumberGenerator.generate()).thenReturn(ORDER_NUMBER);
        when(placeOrderService.createPendingOrder(any(), eq(ORDER_NUMBER))).thenReturn(pendingOrder());

        placeOrderFacade.place(command());

        InOrder inOrder = inOrder(orderNumberGenerator, placeOrderService);
        inOrder.verify(orderNumberGenerator).generate();
        inOrder.verify(placeOrderService).createPendingOrder(any(), eq(ORDER_NUMBER));
    }

    @Test
    @DisplayName("결제 실패: 보상 트랜잭션으로 되돌리고 예외를 전파한다")
    void givenPaymentFails_whenPlace_thenCompensatesAndPropagates() {
        when(orderNumberGenerator.generate()).thenReturn(ORDER_NUMBER);
        when(placeOrderService.createPendingOrder(any(), eq(ORDER_NUMBER))).thenReturn(pendingOrder());
        doThrow(new RuntimeException("결제 실패")).when(paymentService).pay(any(), any());

        assertThatThrownBy(() -> placeOrderFacade.place(command()))
                .isInstanceOf(RuntimeException.class);

        verify(orderCompensationService).compensate(ORDER_ID);
    }
}
