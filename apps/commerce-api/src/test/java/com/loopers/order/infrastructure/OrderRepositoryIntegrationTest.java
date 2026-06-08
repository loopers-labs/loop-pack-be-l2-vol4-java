package com.loopers.order.infrastructure;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItemRepository;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.ShippingDestination;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderRepositoryIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderRepositoryIntegrationTest(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ShippingDestination shipping() {
        return ShippingDestination.create("김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동");
    }

    private Order saveOrder(Long userId, String orderNumber, List<OrderItem> items) {
        Order order = orderRepository.save(
                Order.create(userId, orderNumber, shipping(), items)
        );
        items.forEach(item -> {
            item.assignOrder(order.getId());
            orderItemRepository.save(item);
        });
        return order;
    }

    @Test
    @DisplayName("findByUserId 는 해당 사용자의 주문만 주문일시 내림차순으로 반환한다")
    void givenOrdersOfMultipleUsers_whenFindByUserId_thenReturnsOnlyOwnerOrdersSortedDesc() {
        saveOrder(USER_ID, "20260528-000001",
                List.of(OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 1)));
        saveOrder(USER_ID, "20260528-000002",
                List.of(OrderItem.create(20L, "바지", 1L, "루퍼스", 15_000L, 2)));
        saveOrder(OTHER_USER_ID, "20260528-000003",
                List.of(OrderItem.create(30L, "모자", 1L, "루퍼스", 9_000L, 1)));

        List<Order> orders = orderRepository.findByUserId(USER_ID);

        assertThat(orders)
                .hasSize(2)
                .extracting(Order::getUserId)
                .containsOnly(USER_ID);
        assertThat(orders).isSortedAccordingTo(Comparator.comparing(Order::getOrderedAt).reversed());
    }

    @Test
    @DisplayName("findAll 은 전체 주문을 주문일시 내림차순으로 반환한다")
    void givenOrdersOfMultipleUsers_whenFindAll_thenReturnsAllSortedDesc() {
        saveOrder(USER_ID, "20260528-000001",
                List.of(OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 1)));
        saveOrder(OTHER_USER_ID, "20260528-000002",
                List.of(OrderItem.create(20L, "바지", 1L, "루퍼스", 15_000L, 1)));

        List<Order> orders = orderRepository.findAll();

        assertThat(orders).hasSize(2);
        assertThat(orders).isSortedAccordingTo(Comparator.comparing(Order::getOrderedAt).reversed());
    }

    @Test
    @DisplayName("저장된 주문의 총액은 항목 소계의 합이다")
    void givenOrderWithItems_whenSaved_thenTotalAmountIsSumOfSubtotals() {
        saveOrder(USER_ID, "20260528-000001", List.of(
                OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 2),
                OrderItem.create(20L, "바지", 1L, "루퍼스", 15_000L, 1)
        ));

        List<Order> orders = orderRepository.findByUserId(USER_ID);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getTotalAmount().value()).isEqualTo(29_000L * 2 + 15_000L);
    }

    @Test
    @DisplayName("findByOrderId 는 주문에 속한 항목 스냅샷을 반환한다")
    void givenSavedOrderItems_whenFindByOrderId_thenReturnsItems() {
        Order order = saveOrder(USER_ID, "20260528-000001", List.of(
                OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 2),
                OrderItem.create(20L, "바지", 1L, "루퍼스", 15_000L, 1)
        ));

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        assertThat(items)
                .hasSize(2)
                .extracting(OrderItem::getProductName)
                .containsExactlyInAnyOrder("셔츠", "바지");
    }
}
