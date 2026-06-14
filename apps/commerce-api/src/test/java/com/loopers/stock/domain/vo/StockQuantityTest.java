package com.loopers.stock.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class StockQuantityTest {

    @DisplayName("0 이상의 수량이 주어지면, 재고 수량을 생성한다.")
    @Test
    void createsStockQuantity_whenValueIsNotNegative() {
        // arrange
        int value = 10;

        // act
        StockQuantity quantity = StockQuantity.of(value);

        // assert
        assertThat(quantity.value()).isEqualTo(value);
    }

    @DisplayName("수량이 음수이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsNegative() {
        // arrange
        int value = -1;

        // act & assert
        assertThatThrownBy(() -> StockQuantity.of(value))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("차감 수량만큼 재고 수량을 줄인 새 값을 반환한다.")
    @Test
    void returnsDeductedQuantity_whenStockIsEnough() {
        // arrange
        StockQuantity quantity = StockQuantity.of(10);
        int deductQuantity = 2;

        // act
        StockQuantity deducted = quantity.deduct(deductQuantity);

        // assert
        assertAll(
            () -> assertThat(deducted.value()).isEqualTo(8),
            () -> assertThat(quantity.value()).isEqualTo(10)
        );
    }

    @DisplayName("차감 수량이 0 이하이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenDeductQuantityIsNotPositive() {
        // arrange
        StockQuantity quantity = StockQuantity.of(10);
        int deductQuantity = 0;

        // act & assert
        assertThatThrownBy(() -> quantity.deduct(deductQuantity))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("재고보다 큰 수량을 차감하면, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenDeductQuantityIsGreaterThanStock() {
        // arrange
        StockQuantity quantity = StockQuantity.of(10);
        int deductQuantity = 11;

        // act & assert
        assertThatThrownBy(() -> quantity.deduct(deductQuantity))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }
}
