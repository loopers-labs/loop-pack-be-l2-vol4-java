package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
}
