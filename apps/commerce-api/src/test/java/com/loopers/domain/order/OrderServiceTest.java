package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService();
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {
        @DisplayName("모든 상품의 재고가 충분하면, 재고를 차감하고 주문을 생성한다.")
        @Test
        void createsOrderAndDeductsStock_whenAllProductsAreAvailable() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            OrderResult result = orderService.createOrder(
                "user1234",
                List.of(new OrderProductCommand(1L, 2)),
                Map.of(1L, product)
            );

            // assert
            OrderModel order = result.order();
            assertAll(
                () -> assertThat(order.getOrderLines()).hasSize(1),
                () -> assertThat(order.getTotalAmount()).isEqualTo(60_000L),
                () -> assertThat(product.getStock()).isEqualTo(8),
                () -> assertThat(result.failures()).isEmpty()
            );
        }

        @DisplayName("일부 상품의 재고가 부족하면, 가능한 상품만 주문하고 실패 상품을 결과에 남긴다.")
        @Test
        void createsAvailableOrderLinesAndReturnsFailures_whenSomeProductsAreOutOfStock() {
            // arrange
            ProductModel availableProduct = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 10);
            ProductModel outOfStockProduct = new ProductModel(20L, "셔츠", "가벼운 셔츠", 20_000L, 1);

            // act
            OrderResult result = orderService.createOrder(
                "user1234",
                List.of(
                    new OrderProductCommand(1L, 2),
                    new OrderProductCommand(2L, 3)
                ),
                Map.of(1L, availableProduct, 2L, outOfStockProduct)
            );

            // assert
            assertAll(
                () -> assertThat(result.order().getOrderLines()).hasSize(1),
                () -> assertThat(result.order().getOrderLines().get(0).getProductId()).isEqualTo(1L),
                () -> assertThat(result.failures()).hasSize(1),
                () -> assertThat(result.failures().get(0).productId()).isEqualTo(2L),
                () -> assertThat(availableProduct.getStock()).isEqualTo(8),
                () -> assertThat(outOfStockProduct.getStock()).isEqualTo(1)
            );
        }

        @DisplayName("상품을 찾을 수 없으면, 실패 상품을 결과에 남긴다.")
        @Test
        void returnsFailure_whenProductDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderService.createOrder(
                    "user1234",
                    List.of(new OrderProductCommand(1L, 2)),
                    Map.of()
                );
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("주문 가능한 상품이 하나도 없으면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenNoProductCanBeOrdered() {
            // arrange
            ProductModel product = new ProductModel(10L, "니트", "부드러운 니트", 30_000L, 1);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderService.createOrder("user1234", List.of(new OrderProductCommand(1L, 2)), Map.of(1L, product));
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(product.getStock()).isEqualTo(1)
            );
        }

        @DisplayName("주문 요청 상품이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenCommandIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderService.createOrder("user1234", List.of(), Map.of());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
