package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private OrderService orderService;

    private static final ProductSnapshot SNAPSHOT =
        new ProductSnapshot("나이키 신발", 50000L, "나이키");

    @BeforeEach
    void setUp() {
        orderService = new OrderService(new FakeOrderRepository());
    }

    private List<OrderItem> singleItem() {
        return List.of(new OrderItem(1L, 2, SNAPSHOT));
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 항목이면 PAID 상태로 저장된다.")
        @Test
        void creates_withPaidStatus() {
            Order order = orderService.createOrder(1L, singleItem());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getId()).isNotNull();
        }
    }

    @DisplayName("주문을 단건 조회할 때,")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문이면 반환된다.")
        @Test
        void returns_whenExists() {
            Order saved = orderService.createOrder(1L, singleItem());
            Order found = orderService.getOrder(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderService.getOrder(999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("본인 주문을 조회할 때,")
    @Nested
    class GetOrderForUser {

        @DisplayName("타인 주문 접근 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotOwner() {
            Order saved = orderService.createOrder(1L, singleItem());
            CoreException result = assertThrows(CoreException.class,
                () -> orderService.getOrderForUser(saved.getId(), 2L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class CancelOrder {

        @DisplayName("PAID 주문이면 CANCELLED로 변경된다.")
        @Test
        void cancels_whenPaid() {
            Order saved = orderService.createOrder(1L, singleItem());
            Order cancelled = orderService.cancelOrder(saved.getId(), 1L);
            assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 취소된 주문은 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyCancelled() {
            Order saved = orderService.createOrder(1L, singleItem());
            orderService.cancelOrder(saved.getId(), 1L);

            CoreException result = assertThrows(CoreException.class,
                () -> orderService.cancelOrder(saved.getId(), 1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("기간별 주문 목록 조회 시,")
    @Nested
    class GetOrdersByPeriod {

        @DisplayName("해당 유저의 주문만 반환된다.")
        @Test
        void returns_ordersForUser() {
            orderService.createOrder(1L, singleItem());
            orderService.createOrder(1L, singleItem());
            orderService.createOrder(2L, singleItem());

            List<Order> orders = orderService.getOrdersByPeriod(
                1L,
                ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().plusDays(1)
            );
            assertThat(orders).hasSize(2);
            assertThat(orders).allMatch(o -> o.getUserId().equals(1L));
        }
    }

    static class FakeOrderRepository implements OrderRepository {
        private final Map<Long, Order> store = new HashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);

        @Override
        public Order save(Order order) {
            if (order.getId() == 0L) {
                ReflectionTestUtils.setField(order, "id", idSequence.getAndIncrement());
            }
            if (order.getCreatedAt() == null) {
                ReflectionTestUtils.setField(order, "createdAt", ZonedDateTime.now());
            }
            store.put(order.getId(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Order> findByUserIdAndOrderedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
            return store.values().stream()
                .filter(o -> o.getUserId().equals(userId))
                .filter(o -> o.getCreatedAt() != null &&
                    !o.getCreatedAt().isBefore(startAt) &&
                    !o.getCreatedAt().isAfter(endAt))
                .toList();
        }
    }
}
