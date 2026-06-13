package com.loopers.domain.order;

import com.loopers.application.order.OrderService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 정보를 주면, 주문과 주문 상품이 함께 생성된다.")
        @Test
        void createsOrderWithItems_whenValidInfoIsProvided() {
            List<OrderItem> items = List.of(
                new OrderItem(1L, "청바지", BigDecimal.valueOf(50000), 2)
            );

            Order order = orderService.createOrder(1L, null, BigDecimal.valueOf(100000), BigDecimal.valueOf(10000), items);

            List<OrderItem> savedItems = orderService.getOrderItems(order.getId());
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(100000)),
                () -> assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                () -> assertThat(order.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(90000)),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(savedItems).hasSize(1),
                () -> assertThat(savedItems.get(0).getOrderId()).isEqualTo(order.getId()),
                () -> assertThat(savedItems.get(0).getProductName()).isEqualTo("청바지")
            );
        }
    }

    @DisplayName("주문을 단건 조회할 때,")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문 ID를 주면, 주문 정보를 반환한다.")
        @Test
        void returnsOrder_whenValidIdIsProvided() {
            Order created = orderService.createOrder(1L, null, BigDecimal.valueOf(50000), BigDecimal.ZERO,
                List.of(new OrderItem(1L, "청바지", BigDecimal.valueOf(50000), 1)));

            Order result = orderService.getOrder(created.getId());

            assertThat(result.getId()).isEqualTo(created.getId());
        }

        @DisplayName("존재하지 않는 주문 ID를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class,
                () -> orderService.getOrder(9999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 상품 목록을 조회할 때,")
    @Nested
    class GetOrderItems {

        @DisplayName("주문 ID에 해당하는 주문 상품 목록을 반환한다.")
        @Test
        void returnsOrderItems_byOrderId() {
            List<OrderItem> items = List.of(
                new OrderItem(1L, "청바지", BigDecimal.valueOf(50000), 2),
                new OrderItem(2L, "티셔츠", BigDecimal.valueOf(15000), 1)
            );
            Order order = orderService.createOrder(1L, null, BigDecimal.valueOf(115000), BigDecimal.ZERO, items);

            List<OrderItem> result = orderService.getOrderItems(order.getId());

            assertThat(result).hasSize(2);
        }
    }
}
