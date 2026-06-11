package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemsTest {

    @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenItemsAreEmpty() {
        // act & assert
        assertThatThrownBy(() -> OrderItems.of(List.of()))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("같은 상품 ID의 주문 항목이 중복되면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenProductIdIsDuplicated() {
        // arrange
        OrderItem first = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 1);
        OrderItem second = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 2);

        // act & assert
        assertThatThrownBy(() -> OrderItems.of(List.of(first, second)))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("주문 항목들이 주어지면, 전체 금액을 계산한다.")
    @Test
    void calculatesTotalPrice_whenItemsAreProvided() {
        // arrange
        OrderItem iphone = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", 1_550_000L, 2);
        OrderItem iphoneMax = OrderItem.create(1L, "애플", 2L, "아이폰 16 Pro Max", 1_900_000L, 1);

        // act
        long totalPrice = OrderItems.of(List.of(iphone, iphoneMax)).calculateTotalPrice();

        // assert
        assertThat(totalPrice).isEqualTo(5_000_000L);
    }
}
