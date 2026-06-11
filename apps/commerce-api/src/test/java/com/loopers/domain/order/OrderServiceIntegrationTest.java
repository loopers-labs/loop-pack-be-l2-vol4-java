package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductStockService productStockService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 PENDING 생성할 때")
    @Nested
    class CreatePendingOrder {

        @DisplayName("정상 입력이면, 주문이 PENDING으로 저장되고 재고가 차감된다.")
        @Test
        void createsOrder_andDecreasesStock() {
            // given
            ProductModel product = productService.createProduct(1L, "티셔츠", "면 100%", 10000L, 10);

            // when
            OrderModel order = orderService.createPendingOrder(99L, List.of(OrderLine.of(product.getId(), 3)));

            // then
            assertAll(
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(order.getTotalAmount()).isEqualTo(30000L),
                    () -> assertThat(order.getOrderItems()).hasSize(1),
                    () -> assertThat(productStockService.getStock(product.getId()).getStock().value()).isEqualTo(7)
            );
        }

        @DisplayName("재고보다 많은 수량이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenInsufficientStock() {
            // given
            ProductModel product = productService.createProduct(1L, "티셔츠", "면 100%", 10000L, 2);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderService.createPendingOrder(99L, List.of(OrderLine.of(product.getId(), 5))));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> orderService.createPendingOrder(99L, List.of(OrderLine.of(99999L, 1))));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 확정할 때")
    @Nested
    class Confirm {

        @DisplayName("PENDING 상태의 주문이 PAID 상태로 변경된다.")
        @Test
        void transitionsToPaid() {
            // given
            ProductModel product = productService.createProduct(1L, "티셔츠", "면 100%", 10000L, 5);
            OrderModel pending = orderService.createPendingOrder(99L, List.of(OrderLine.of(product.getId(), 1)));

            // when
            OrderModel result = orderService.confirm(pending.getId());

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }

    @DisplayName("주문 실패 처리할 때")
    @Nested
    class Fail {

        @DisplayName("재고가 복구되고, PENDING 상태의 주문이 FAILED 상태로 변경된다.")
        @Test
        void restoresStock_andMarksFailed() {
            // given
            ProductModel product = productService.createProduct(1L, "티셔츠", "면 100%", 10000L, 5);
            OrderModel pending = orderService.createPendingOrder(99L, List.of(OrderLine.of(product.getId(), 3)));

            // when
            OrderModel result = orderService.fail(pending.getId());

            // then
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED),
                    () -> assertThat(productStockService.getStock(product.getId()).getStock().value()).isEqualTo(5)
            );
        }
    }
}