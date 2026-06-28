package com.loopers.domain.order;

import com.loopers.domain.product.ProductSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private OrderService orderService;
    private FakeOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository = new FakeOrderRepository();
        orderService = new OrderService(orderRepository);
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 스냅샷이 주어지면 주문이 생성된다.")
        @Test
        void createsOrder_withSnapshots() {
            List<ProductSnapshot> snapshots = List.of(
                new ProductSnapshot(1L, "상품A", 5000L, 3)
            );

            Order order = orderService.createOrder(1L, snapshots);

            assertAll(
                () -> assertThat(order).isNotNull(),
                () -> assertThat(order.getItems()).hasSize(1),
                () -> assertThat(order.getTotalAmount()).isEqualTo(15000L),
                () -> assertThat(order.getItems().get(0).getProductNameSnapshot()).isEqualTo("상품A"),
                () -> assertThat(order.getItems().get(0).getProductPriceSnapshot()).isEqualTo(5000L)
            );
        }

        @DisplayName("여러 상품 스냅샷이 주어지면 총 금액이 합산된다.")
        @Test
        void calculatesTotalAmount_fromMultipleSnapshots() {
            List<ProductSnapshot> snapshots = List.of(
                new ProductSnapshot(1L, "상품A", 5000L, 2),
                new ProductSnapshot(2L, "상품B", 3000L, 1)
            );

            Order order = orderService.createOrder(1L, snapshots);

            assertThat(order.getTotalAmount()).isEqualTo(13000L);
        }

        @DisplayName("생성된 주문의 상태는 PENDING이다.")
        @Test
        void orderStatus_isPending_whenCreated() {
            List<ProductSnapshot> snapshots = List.of(new ProductSnapshot(1L, "상품A", 1000L, 1));
            Order order = orderService.createOrder(1L, snapshots);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }

    @DisplayName("주문 소유자 확인 시,")
    @Nested
    class IsOwnedBy {

        @DisplayName("동일한 userId이면 true를 반환한다.")
        @Test
        void returnsTrue_whenSameUser() {
            Order order = new Order(1L, 5000L, List.of(new OrderItem(1L, "상품A", 5000L, 1)));
            assertThat(order.isOwnedBy(1L)).isTrue();
        }

        @DisplayName("다른 userId이면 false를 반환한다.")
        @Test
        void returnsFalse_whenDifferentUser() {
            Order order = new Order(1L, 5000L, List.of(new OrderItem(1L, "상품A", 5000L, 1)));
            assertThat(order.isOwnedBy(99L)).isFalse();
        }
    }

    static class FakeOrderRepository implements OrderRepository {
        private final List<Order> store = new ArrayList<>();

        @Override
        public Order save(Order order) { store.add(order); return order; }

        @Override
        public Optional<Order> findById(Long id) {
            return store.stream().filter(o -> Objects.equals(o.getId(), id)).findFirst();
        }

        @Override
        public List<Order> findByUserId(Long userId, String startAt, String endAt) {
            return store.stream().filter(o -> Objects.equals(o.getUserId(), userId)).toList();
        }

        @Override
        public Page<Order> findAll(Pageable pageable) {
            return Page.empty();
        }
    }
}
