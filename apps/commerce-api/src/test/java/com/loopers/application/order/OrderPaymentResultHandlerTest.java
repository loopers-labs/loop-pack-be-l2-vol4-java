package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.stock.StockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentResultHandlerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private StockService stockService;

    @Mock
    private CouponService couponService;

    @InjectMocks
    private OrderPaymentResultHandler handler;

    private OrderItem item(long productId, long unitPrice, int quantity) {
        return new OrderItem(productId, "상품-" + productId, unitPrice, quantity);
    }

    @DisplayName("결제 성공(onPaid) 시")
    @Nested
    class OnPaid {

        @DisplayName("주문을 PAID로 전이시킨다")
        @Test
        void marksOrderPaid() {
            handler.onPaid(1L);

            verify(orderService).pay(1L);
        }
    }

    @DisplayName("결제 실패(onFailed) 시")
    @Nested
    class OnFailed {

        @DisplayName("CREATED 주문이면 재고·쿠폰을 복원하고 주문을 취소한다")
        @Test
        void restoresAndCancels() {
            OrderModel order = new OrderModel(1L, List.of(item(100L, 1_000L, 2)), 10L, Money.of(500L));
            when(orderService.getById(1L)).thenReturn(order);

            handler.onFailed(1L);

            verify(stockService).increase(100L, 2);
            verify(couponService).cancel(10L);
            verify(orderService).cancel(1L);
        }

        @DisplayName("쿠폰이 없으면 재고만 복원하고 쿠폰 복원은 호출하지 않는다")
        @Test
        void restoresStockOnly_whenNoCoupon() {
            OrderModel order = new OrderModel(1L, List.of(item(100L, 1_000L, 2)), null, Money.ZERO);
            when(orderService.getById(1L)).thenReturn(order);

            handler.onFailed(1L);

            verify(stockService).increase(100L, 2);
            verify(couponService, never()).cancel(anyLong());
            verify(orderService).cancel(1L);
        }

        @DisplayName("이미 CANCELED 주문이면 재고·쿠폰 복원도 취소도 하지 않는다 (멱등)")
        @Test
        void isIdempotent_whenAlreadyCanceled() {
            OrderModel order = new OrderModel(1L, List.of(item(100L, 1_000L, 2)), null, Money.ZERO);
            order.cancel();
            when(orderService.getById(1L)).thenReturn(order);

            handler.onFailed(1L);

            verify(stockService, never()).increase(anyLong(), anyInt());
            verify(couponService, never()).cancel(anyLong());
            verify(orderService, never()).cancel(any());
        }
    }
}
