package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderService orderService;

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("주문 항목의 가격 합계가 총금액으로 계산되어 저장된다.")
        @Test
        void calculates_total_amount_from_items() {
            // arrange
            List<OrderItemCommand> items = List.of(
                new OrderItemCommand(1L, "상품A", 10000L, 2),  // 20000
                new OrderItemCommand(2L, "상품B", 5000L, 1)    // 5000
            );
            OrderModel savedOrder = new OrderModel(1L, 25000L, 0L, null);
            when(orderRepository.save(any(OrderModel.class))).thenReturn(savedOrder);
            when(orderItemRepository.saveAll(anyList())).thenReturn(List.of());

            // act
            OrderModel result = orderService.createOrder(1L, items, null, 0L);

            // assert
            ArgumentCaptor<OrderModel> captor = ArgumentCaptor.forClass(OrderModel.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getTotalAmount()).isEqualTo(25000L);
        }

        @DisplayName("주문 항목이 모두 저장된다.")
        @Test
        void saves_all_order_items() {
            // arrange
            List<OrderItemCommand> items = List.of(
                new OrderItemCommand(1L, "상품A", 10000L, 1),
                new OrderItemCommand(2L, "상품B", 3000L, 2)
            );
            when(orderRepository.save(any(OrderModel.class))).thenReturn(new OrderModel(1L, 16000L, 0L, null));
            when(orderItemRepository.saveAll(anyList())).thenReturn(List.of());

            // act
            orderService.createOrder(1L, items, null, 0L);

            // assert
            ArgumentCaptor<List<OrderItemModel>> itemsCaptor = ArgumentCaptor.forClass(List.class);
            verify(orderItemRepository).saveAll(itemsCaptor.capture());
            assertThat(itemsCaptor.getValue()).hasSize(2);
        }

        @DisplayName("생성된 주문의 상태는 PENDING이다.")
        @Test
        void created_order_has_pending_status() {
            // arrange
            List<OrderItemCommand> items = List.of(
                new OrderItemCommand(1L, "상품A", 10000L, 1)
            );
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.saveAll(anyList())).thenReturn(List.of());

            // act
            OrderModel result = orderService.createOrder(1L, items, null, 0L);

            // assert
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }

    @DisplayName("주문을 단건 조회할 때,")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문 ID이면 주문을 반환한다.")
        @Test
        void returns_order_when_id_exists() {
            // arrange
            OrderModel order = new OrderModel(1L, 10000L, 0L, null);
            when(orderRepository.find(0L)).thenReturn(Optional.of(order));

            // act
            OrderModel result = orderService.getOrder(0L);

            // assert
            assertAll(
                () -> assertThat(result.getMemberId()).isEqualTo(1L),
                () -> assertThat(result.getTotalAmount()).isEqualTo(10000L)
            );
        }

        @DisplayName("존재하지 않는 주문 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_not_found_when_id_not_exist() {
            // arrange
            when(orderRepository.find(999L)).thenReturn(Optional.empty());

            // act & assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.getOrder(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 목록을 조회할 때,")
    @Nested
    class GetOrders {

        @DisplayName("날짜 범위 없이 조회하면 회원의 전체 주문을 반환한다.")
        @Test
        void returns_all_orders_when_no_date_range() {
            // arrange
            List<OrderModel> orders = List.of(new OrderModel(1L, 10000L, 0L, null), new OrderModel(1L, 20000L, 0L, null));
            when(orderRepository.findAllByMemberId(1L)).thenReturn(orders);

            // act
            List<OrderModel> result = orderService.getOrders(1L, null, null);

            // assert
            assertThat(result).hasSize(2);
            verify(orderRepository).findAllByMemberId(1L);
        }

        @DisplayName("날짜 범위를 주면 해당 범위의 주문만 반환한다.")
        @Test
        void returns_filtered_orders_when_date_range_provided() {
            // arrange
            ZonedDateTime start = ZonedDateTime.now().minusDays(7);
            ZonedDateTime end = ZonedDateTime.now();
            List<OrderModel> orders = List.of(new OrderModel(1L, 10000L, 0L, null));
            when(orderRepository.findAllByMemberIdAndCreatedAtBetween(1L, start, end)).thenReturn(orders);

            // act
            List<OrderModel> result = orderService.getOrders(1L, start, end);

            // assert
            assertThat(result).hasSize(1);
            verify(orderRepository).findAllByMemberIdAndCreatedAtBetween(1L, start, end);
        }
    }

    @DisplayName("주문 항목을 조회할 때,")
    @Nested
    class GetOrderItems {

        @DisplayName("주문 ID로 해당 주문의 항목 목록을 반환한다.")
        @Test
        void returns_items_for_order() {
            // arrange
            List<OrderItemModel> items = List.of(
                new OrderItemModel(1L, 1L, "상품A", 10000L, 2),
                new OrderItemModel(1L, 2L, "상품B", 5000L, 1)
            );
            when(orderItemRepository.findAllByOrderId(1L)).thenReturn(items);

            // act
            List<OrderItemModel> result = orderService.getOrderItems(1L);

            // assert
            assertThat(result).hasSize(2);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class CancelOrder {

        @DisplayName("PENDING 상태의 주문을 취소하면 CANCELLED 상태가 된다.")
        @Test
        void cancels_pending_order() {
            // arrange
            OrderModel order = new OrderModel(1L, 10000L, 0L, null);
            when(orderRepository.find(0L)).thenReturn(Optional.of(order));

            // act
            orderService.cancelOrder(0L);

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("존재하지 않는 주문을 취소하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_not_found_when_order_not_exist() {
            // arrange
            when(orderRepository.find(999L)).thenReturn(Optional.empty());

            // act & assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.cancelOrder(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("CONFIRMED 상태의 주문을 취소하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_cancelling_confirmed_order() {
            // arrange
            OrderModel order = new OrderModel(1L, 10000L, 0L, null);
            order.confirm();
            when(orderRepository.find(0L)).thenReturn(Optional.of(order));

            // act & assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.cancelOrder(0L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}