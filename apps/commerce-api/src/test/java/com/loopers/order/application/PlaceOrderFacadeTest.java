package com.loopers.order.application;

import com.loopers.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceOrderFacadeTest {

    private static final Long ORDER_ID = 100L;
    private static final String ORDER_NUMBER = "20260528-000001";

    private final PlaceOrderService placeOrderService = mock(PlaceOrderService.class);
    private final OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);

    private final PlaceOrderFacade placeOrderFacade =
            new PlaceOrderFacade(placeOrderService, orderNumberGenerator);

    private OrderCommand.Create command() {
        return new OrderCommand.Create(
                1L, List.of(new OrderCommand.Line(10L, 2)),
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동", 50L
        );
    }

    private OrderResult.Detail pendingOrder() {
        return new OrderResult.Detail(
                ORDER_ID, ORDER_NUMBER, OrderStatus.PENDING_PAYMENT,
                58_000L, 3_000L, 55_000L, 50L,
                ZonedDateTime.now(),
                new OrderResult.Recipient("김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동"),
                List.of()
        );
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
}
