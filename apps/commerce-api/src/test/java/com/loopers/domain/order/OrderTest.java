package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderTest {

    @DisplayName("사용자 ID와 주문 항목들이 주어지면, 주문을 생성하고 전체 금액을 계산한다.")
    @Test
    void createsOrder_whenUserIdAndItemsAreProvided() {
        // arrange
        Long userId = 1L;
        OrderItem iphone = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 2);
        OrderItem iphoneMax = OrderItem.create(1L, "애플", 2L, "아이폰 16 Pro Max", 1_900_000L, 1);

        // act
        Order order = Order.create(userId, List.of(iphone, iphoneMax));

        // assert
        assertAll(
            () -> assertThat(order.getUserId()).isEqualTo(userId),
            () -> assertThat(order.getItems()).containsExactly(iphone, iphoneMax),
            () -> assertThat(order.getOrderTotalPrice()).isEqualTo(5_000_000L),
            () -> assertThat(order.isOrderedBy(userId)).isTrue(),
            () -> assertThat(order.isOrderedBy(2L)).isFalse()
        );
    }

    @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenItemsAreEmpty() {
        // arrange
        Long userId = 1L;

        // act & assert
        assertThatThrownBy(() -> Order.create(userId, List.of()))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("같은 상품 ID의 주문 항목이 중복되면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenProductIdIsDuplicated() {
        // arrange
        Long userId = 1L;
        OrderItem first = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 1);
        OrderItem second = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 2);

        // act & assert
        assertThatThrownBy(() -> Order.create(userId, List.of(first, second)))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("사용자 ID가 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenUserIdIsNull() {
        // arrange
        OrderItem item = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 1);

        // act & assert
        assertThatThrownBy(() -> Order.create(null, List.of(item)))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
