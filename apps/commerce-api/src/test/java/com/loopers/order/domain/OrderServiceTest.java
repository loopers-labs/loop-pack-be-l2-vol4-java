package com.loopers.order.domain;

import com.loopers.product.domain.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService();
    }

    @DisplayName("getOrThrow를 호출할 때,")
    @Nested
    class GetOrThrow {

        @DisplayName("order가 존재하면, 해당 order를 반환한다.")
        @Test
        void returnsOrder_whenOrderExists() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(
                new OrderItemModel(1L, "에어맥스", 150000L, 2)
            ));

            // act
            OrderModel result = orderService.getOrThrow(Optional.of(order));

            // assert
            assertThat(result).isEqualTo(order);
        }

        @DisplayName("order가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.getOrThrow(Optional.empty())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("createOrder를 호출할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, 재고를 차감하고 OrderModel을 반환한다.")
        @Test
        void returnsOrderModel_andDecreasesStock_whenRequestIsValid() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 10, null);
            Map<Long, Integer> quantities = Map.of(product.getId(), 2);

            // act
            OrderModel result = orderService.createOrder(1L, List.of(product), quantities);

            // assert
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo(1L),
                () -> assertThat(result.getItems()).hasSize(1),
                () -> assertThat(result.getItems().get(0).getProductName()).isEqualTo("에어맥스"),
                () -> assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2),
                () -> assertThat(product.getStock()).isEqualTo(8)
            );
        }

        @DisplayName("재고가 부족한 상품이 포함된 경우, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 1, null);
            Map<Long, Integer> quantities = Map.of(product.getId(), 5);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderService.createOrder(1L, List.of(product), quantities)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
