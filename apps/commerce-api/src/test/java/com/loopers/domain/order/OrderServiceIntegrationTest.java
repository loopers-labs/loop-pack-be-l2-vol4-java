package com.loopers.domain.order;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderServiceIntegrationTest {

    private final OrderService orderService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderServiceIntegrationTest(
        OrderService orderService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderService = orderService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 생성할 때 ")
    @Nested
    class CreateOrder {

        @DisplayName("사용자 ID와 주문 항목들이 주어지면, 주문과 주문 항목을 함께 저장한다.")
        @Test
        void savesOrderAndItems_whenUserIdAndItemsAreProvided() {
            // arrange
            Long userId = 1L;
            OrderItem iphone = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 2);
            OrderItem iphoneMax = OrderItem.create(1L, "애플", 2L, "아이폰 16 Pro Max", 1_900_000L, 1);

            // act
            Order saved = orderService.createOrder(userId, List.of(iphone, iphoneMax));
            Order found = orderService.getOrder(saved.getId());

            // assert
            assertAll(
                () -> assertThat(found.getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.getUserId()).isEqualTo(userId),
                () -> assertThat(found.getOrderTotalPrice()).isEqualTo(5_000_000L),
                () -> assertThat(found.getItems())
                    .extracting(OrderItem::getProductName)
                    .containsExactly("아이폰 16 Pro", "아이폰 16 Pro Max"),
                () -> assertThat(found.getItems())
                    .extracting(OrderItem::getTotalPrice)
                    .containsExactly(3_100_000L, 1_900_000L)
            );
        }
    }
}
