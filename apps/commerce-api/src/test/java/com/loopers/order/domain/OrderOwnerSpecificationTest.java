package com.loopers.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderOwnerSpecificationTest {

    @DisplayName("isSatisfiedBy를 호출할 때,")
    @Nested
    class IsSatisfiedBy {

        @DisplayName("주문의 userId와 요청 userId가 같으면, true를 반환한다.")
        @Test
        void returnsTrue_whenUserIdMatches() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));
            OrderOwnerSpecification spec = new OrderOwnerSpecification(1L);

            // act & assert
            assertThat(spec.isSatisfiedBy(order)).isTrue();
        }

        @DisplayName("주문의 userId와 요청 userId가 다르면, false를 반환한다.")
        @Test
        void returnsFalse_whenUserIdNotMatches() {
            // arrange
            OrderModel order = new OrderModel(1L, List.of(new OrderItemModel(1L, "에어맥스", 150000L, 1)));
            OrderOwnerSpecification spec = new OrderOwnerSpecification(999L);

            // act & assert
            assertThat(spec.isSatisfiedBy(order)).isFalse();
        }
    }
}
