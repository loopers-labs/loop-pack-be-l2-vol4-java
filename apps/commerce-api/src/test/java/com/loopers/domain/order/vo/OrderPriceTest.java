package com.loopers.domain.order.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderPriceTest {

    @DisplayName("0 이상의 금액이 주어지면, 주문 금액을 생성한다.")
    @Test
    void createsOrderPrice_whenValueIsNotNegative() {
        // arrange
        long value = 1_550_000L;

        // act
        OrderPrice price = OrderPrice.of(value);

        // assert
        assertThat(price.value()).isEqualTo(value);
    }

    @DisplayName("주문 금액이 음수이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsNegative() {
        // arrange
        long value = -1L;

        // act & assert
        assertThatThrownBy(() -> OrderPrice.of(value))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("주문 금액에 수량을 곱하면, 계산된 주문 금액을 반환한다.")
    @Test
    void returnsMultipliedPrice_whenQuantityIsProvided() {
        // arrange
        OrderPrice price = OrderPrice.of(1_550_000L);
        OrderQuantity quantity = OrderQuantity.of(2);

        // act
        OrderPrice multiplied = price.multiply(quantity);

        // assert
        assertAll(
            () -> assertThat(multiplied.value()).isEqualTo(3_100_000L),
            () -> assertThat(price.value()).isEqualTo(1_550_000L)
        );
    }

    @DisplayName("주문 금액끼리 더하면, 합산된 주문 금액을 반환한다.")
    @Test
    void returnsAddedPrice_whenOtherPriceIsProvided() {
        // arrange
        OrderPrice price = OrderPrice.of(1_550_000L);
        OrderPrice other = OrderPrice.of(1_900_000L);

        // act
        OrderPrice added = price.add(other);

        // assert
        assertThat(added.value()).isEqualTo(3_450_000L);
    }
}
