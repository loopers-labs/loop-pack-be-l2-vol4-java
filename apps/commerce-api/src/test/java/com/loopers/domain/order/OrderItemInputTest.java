package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemInputTest {

    @DisplayName("OrderItemInput 병합(merge) 시,")
    @Nested
    class Merge {

        @DisplayName("단일 입력이면, 그대로 1개의 OrderItemInput이 반환된다.")
        @Test
        void returnsSingleInput_whenSingleInputGiven() {
            List<OrderItemInput> inputs = List.of(new OrderItemInput(1L, 3));

            List<OrderItemInput> result = OrderItemInput.merge(inputs);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).stockId()).isEqualTo(1L);
            assertThat(result.get(0).quantity()).isEqualTo(3);
        }

        @DisplayName("동일한 stockId가 중복되면, 수량이 합산되어 1개의 OrderItemInput이 반환된다.")
        @Test
        void mergesQuantity_whenDuplicateStockIdGiven() {
            List<OrderItemInput> inputs = List.of(
                    new OrderItemInput(1L, 2),
                    new OrderItemInput(1L, 3)
            );

            List<OrderItemInput> result = OrderItemInput.merge(inputs);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).stockId()).isEqualTo(1L);
            assertThat(result.get(0).quantity()).isEqualTo(5);
        }

        @DisplayName("서로 다른 stockId이면, 각각의 OrderItemInput이 반환된다.")
        @Test
        void returnsMultipleInputs_whenDifferentStockIdsGiven() {
            List<OrderItemInput> inputs = List.of(
                    new OrderItemInput(1L, 2),
                    new OrderItemInput(2L, 3)
            );

            List<OrderItemInput> result = OrderItemInput.merge(inputs);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(OrderItemInput::stockId).containsExactlyInAnyOrder(1L, 2L);
        }

        @DisplayName("빈 입력이면, 빈 리스트가 반환된다.")
        @Test
        void returnsEmptyList_whenEmptyInputGiven() {
            List<OrderItemInput> result = OrderItemInput.merge(List.of());

            assertThat(result).isEmpty();
        }
    }
}
