package com.loopers.order.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderQuantityTest {

    @DisplayName("1 이상의 수량이 주어지면, 주문 수량을 생성한다.")
    @Test
    void createsOrderQuantity_whenValueIsPositive() {
        // arrange
        int value = 2;

        // act
        OrderQuantity quantity = OrderQuantity.of(value);

        // assert
        assertThat(quantity.value()).isEqualTo(value);
    }

    @DisplayName("주문 수량이 0 이하이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsNotPositive() {
        // arrange
        int value = 0;

        // act & assert
        assertThatThrownBy(() -> OrderQuantity.of(value))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
