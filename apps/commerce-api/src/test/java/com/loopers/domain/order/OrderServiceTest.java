package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private OrderItem item(long productId, long unitPrice, int quantity) {
        return new OrderItem(productId, "상품-" + productId, unitPrice, quantity);
    }

    @DisplayName("주문 생성 시")
    @Nested
    class Place {

        @DisplayName("유효한 유저·항목으로 호출하면 status=CREATED 주문이 저장되고 그대로 반환된다")
        @Test
        void savesAndReturnsCreatedOrder() {
            OrderItem a = item(1L, 10_000L, 2);
            OrderItem b = item(2L, 15_000L, 1);
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderModel result = orderService.place(1L, List.of(a, b), null, Money.ZERO);

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(result.getTotalAmount().value()).isEqualTo(35_000L),
                () -> assertThat(result.getItems()).hasSize(2)
            );
        }

        @DisplayName("쿠폰과 할인액을 전달하면 issuedCouponId·discountAmount·finalAmount가 반영된 주문이 저장된다")
        @Test
        void appliesCouponAndDiscount() {
            OrderItem a = item(1L, 10_000L, 2);   // 20_000
            OrderItem b = item(2L, 15_000L, 1);   // 15_000
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderModel result = orderService.place(1L, List.of(a, b), 10L, Money.of(5_000L));

            assertAll(
                () -> assertThat(result.getIssuedCouponId()).isEqualTo(10L),
                () -> assertThat(result.getDiscountAmount().value()).isEqualTo(5_000L),
                () -> assertThat(result.getFinalAmount().value()).isEqualTo(30_000L)
            );
        }
    }

    @DisplayName("getById 시")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문이면 그대로 반환한다")
        @Test
        void returnsOrder_whenIdExists() {
            OrderModel order = new OrderModel(1L, List.of(item(1L, 100L, 1)), null, Money.ZERO);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThat(orderService.getById(1L)).isSameAs(order);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenMissing() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () -> orderService.getById(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("결제 확정(pay) 시")
    @Nested
    class Pay {

        @DisplayName("존재하는 주문이면 상태가 PAID로 전이된다")
        @Test
        void marksPaid_whenExists() {
            OrderModel order = new OrderModel(1L, List.of(item(1L, 100L, 1)), null, Money.ZERO);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            orderService.pay(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenMissing() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () -> orderService.pay(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("취소(cancel) 시")
    @Nested
    class Cancel {

        @DisplayName("존재하는 주문이면 상태가 CANCELED로 전이된다")
        @Test
        void marksCanceled_whenExists() {
            OrderModel order = new OrderModel(1L, List.of(item(1L, 100L, 1)), null, Money.ZERO);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            orderService.cancel(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenMissing() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () -> orderService.cancel(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
