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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderService orderService;

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 lines 가 주어지면, OrderModel 과 OrderItem 이 모두 저장되고 OrderResult 로 반환된다.")
        @Test
        void persistsOrderAndItems_whenLinesAreValid() {
            // given
            Long userId = 1L;
            List<OrderLine> rawLines = List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(200L, 3, "스탠스미스", 50_000L, "아디다스")
            );
            given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> {
                OrderModel order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 999L);
                return order;
            });
            given(orderItemRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // when
            OrderResult result = orderService.create(userId, rawLines);

            // then
            assertAll(
                () -> assertThat(result.order().getUserId()).isEqualTo(userId),
                () -> assertThat(result.order().getTotalAmount()).isEqualTo(100_000L * 2 + 50_000L * 3),
                () -> assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(result.items()).hasSize(2)
            );
            verify(orderRepository).save(any(OrderModel.class));
            verify(orderItemRepository).saveAll(anyList());
        }

        @DisplayName("같은 productId 가 두 번 들어오면, OrderItem 1행으로 합산되어 저장된다.")
        @Test
        void mergesDuplicateProductLines_whenSameProductAppearsTwice() {
            // given
            Long userId = 1L;
            List<OrderLine> rawLines = List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(100L, 3, "에어맥스 270", 100_000L, "나이키")
            );
            given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> {
                OrderModel order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 999L);
                return order;
            });
            given(orderItemRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // when
            OrderResult result = orderService.create(userId, rawLines);

            // then
            assertAll(
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).getQuantity()).isEqualTo(5),
                () -> assertThat(result.order().getTotalAmount()).isEqualTo(100_000L * 5L)
            );
        }

        @DisplayName("lines 가 빈 리스트이면, EMPTY_ORDER_ITEMS 예외가 발생하고 저장이 수행되지 않는다.")
        @Test
        void throwsEmptyOrderItems_whenRawLinesIsEmpty() {
            // given
            Long userId = 1L;
            List<OrderLine> rawLines = List.of();

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.create(userId, rawLines));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EMPTY_ORDER_ITEMS);
            verify(orderRepository, never()).save(any(OrderModel.class));
            verify(orderItemRepository, never()).saveAll(anyList());
        }

        @DisplayName("userId 가 null 이면, BAD_REQUEST 예외가 발생하고 저장이 수행되지 않는다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // given
            Long userId = null;
            List<OrderLine> rawLines = List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키")
            );

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.create(userId, rawLines));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderRepository, never()).save(any(OrderModel.class));
            verify(orderItemRepository, never()).saveAll(anyList());
        }
    }
}
