package com.loopers.domain.order;

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

            OrderModel result = orderService.place(1L, List.of(a, b));

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(result.getTotalAmount().value()).isEqualTo(35_000L),
                () -> assertThat(result.getItems()).hasSize(2)
            );
        }
    }

    @DisplayName("getById 시")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문이면 그대로 반환한다")
        @Test
        void returnsOrder_whenIdExists() {
            OrderModel order = new OrderModel(1L, List.of(item(1L, 100L, 1)));
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
}
