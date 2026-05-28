package com.loopers.domain.order;

import com.loopers.domain.money.Money;
import com.loopers.domain.quantity.Quantity;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderItemJpaRepository orderItemJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 접수할 때, ")
    @Nested
    class Place {
        @DisplayName("Order와 OrderItem들이 저장되고, OrderItem들은 저장된 Order의 id로 매핑된다.")
        @Test
        void persistsOrderAndItemsWithOrderId() {
            // arrange
            Long userId = 1L;
            List<OrderItem> items = List.of(
                new OrderItem(10L, "에어맥스", new Money(BigDecimal.valueOf(1000)), new Quantity(2)),
                new OrderItem(11L, "양말", new Money(BigDecimal.valueOf(500)), new Quantity(1))
            );

            // act
            Order saved = orderService.place(userId, items);

            // assert
            List<OrderItem> persistedItems = orderItemJpaRepository.findAll();
            assertAll(
                () -> assertThat(orderJpaRepository.findById(saved.getId())).isPresent(),
                () -> assertThat(persistedItems).hasSize(2),
                () -> assertThat(persistedItems).extracting(OrderItem::getOrderId)
                    .containsOnly(saved.getId()),
                () -> assertThat(saved.getTotalAmount().getAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(2500))
            );
        }
    }
}
