package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderProductProcessServiceTest {

    private OrderProductProcessService orderProductProcessService;

    @BeforeEach
    void setUp() {
        orderProductProcessService = new OrderProductProcessService();
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {
        @DisplayName("모든 상품의 재고가 충분하면, 재고를 차감하고 주문을 생성한다.")
        @Test
        void createsOrderAndDeductsStock_whenAllProductsAreAvailable() {
            // arrange
            Product product = Product.reconstruct(1L, 10L, "니트", "부드러운 니트", 30_000L, 10, 0, false);

            // act
            OrderResult result = orderProductProcessService.createOrder(
                "user1234",
                List.of(new OrderProductCommand(1L, 2)),
                List.of(product)
            );

            // assert
            Order order = result.order();
            assertAll(
                () -> assertThat(order.getOrderLines()).hasSize(1),
                () -> assertThat(order.getTotalAmount()).isEqualTo(60_000L),
                () -> assertThat(order.getOriginalAmount()).isEqualTo(60_000L),
                () -> assertThat(order.getDiscountAmount()).isZero(),
                () -> assertThat(order.getFinalAmount()).isEqualTo(60_000L),
                () -> assertThat(product.getStock()).isEqualTo(8),
                () -> assertThat(result.failures()).isEmpty()
            );
        }

        @DisplayName("같은 상품이 여러 번 요청되면, 수량을 합산해 주문 라인을 만들고 재고를 차감한다.")
        @Test
        void mergesDuplicateProductCommands() {
            // arrange
            Product product = Product.reconstruct(1L, 10L, "니트", "부드러운 니트", 30_000L, 10, 0, false);

            // act
            OrderResult result = orderProductProcessService.createOrder(
                "user1234",
                List.of(
                    new OrderProductCommand(1L, 2),
                    new OrderProductCommand(1L, 3)
                ),
                List.of(product)
            );

            // assert
            assertAll(
                () -> assertThat(result.order().getOrderLines()).hasSize(1),
                () -> assertThat(result.order().getOrderLines().get(0).getQuantity()).isEqualTo(5),
                () -> assertThat(result.order().getOriginalAmount()).isEqualTo(150_000L),
                () -> assertThat(product.getStock()).isEqualTo(5)
            );
        }

        @DisplayName("같은 상품의 합산 수량이 재고보다 크면, CONFLICT 예외가 발생하고 재고는 변경되지 않는다.")
        @Test
        void throwsConflictException_whenMergedQuantityIsGreaterThanStock() {
            // arrange
            Product product = Product.reconstruct(1L, 10L, "니트", "부드러운 니트", 30_000L, 5, 0, false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderProductProcessService.createOrder(
                    "user1234",
                    List.of(
                        new OrderProductCommand(1L, 3),
                        new OrderProductCommand(1L, 3)
                    ),
                    List.of(product)
                );
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(product.getStock()).isEqualTo(5)
            );
        }

        @DisplayName("일부 상품의 재고가 부족하면, 전체 주문을 실패시키고 재고를 저장 대상으로 만들지 않는다.")
        @Test
        void throwsConflictException_whenSomeProductsAreOutOfStock() {
            // arrange
            Product availableProduct = Product.reconstruct(1L, 10L, "니트", "부드러운 니트", 30_000L, 10, 0, false);
            Product outOfStockProduct = Product.reconstruct(2L, 20L, "셔츠", "가벼운 셔츠", 20_000L, 1, 0, false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderProductProcessService.createOrder(
                    "user1234",
                    List.of(
                        new OrderProductCommand(1L, 2),
                        new OrderProductCommand(2L, 3)
                    ),
                    List.of(availableProduct, outOfStockProduct)
                );
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(availableProduct.getStock()).isEqualTo(10),
                () -> assertThat(outOfStockProduct.getStock()).isEqualTo(1)
            );
        }

        @DisplayName("상품을 찾을 수 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderProductProcessService.createOrder(
                    "user1234",
                    List.of(new OrderProductCommand(1L, 2)),
                    List.of()
                );
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("주문 가능한 상품이 하나도 없으면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenNoProductCanBeOrdered() {
            // arrange
            Product product = Product.reconstruct(1L, 10L, "니트", "부드러운 니트", 30_000L, 1, 0, false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                orderProductProcessService.createOrder("user1234", List.of(new OrderProductCommand(1L, 2)), List.of(product));
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
                orderProductProcessService.createOrder("user1234", List.of(), List.of());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
