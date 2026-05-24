package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductStockTest {

    @DisplayName("상품 ID와 초기 재고가 주어지면, 상품 재고를 생성한다.")
    @Test
    void createsProductStock_whenProductIdAndQuantityAreProvided() {
        // arrange
        Long productId = 1L;
        int quantity = 10;

        // act
        ProductStock productStock = ProductStock.create(productId, quantity);

        // assert
        assertAll(
            () -> assertThat(productStock.getProductId()).isEqualTo(productId),
            () -> assertThat(productStock.getQuantity()).isEqualTo(quantity)
        );
    }

    @DisplayName("초기 재고가 음수이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenQuantityIsNegative() {
        // arrange
        Long productId = 1L;
        int quantity = -1;

        // act & assert
        assertThatThrownBy(() -> ProductStock.create(productId, quantity))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("상품 ID가 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenProductIdIsNull() {
        // arrange
        Long productId = null;
        int quantity = 10;

        // act & assert
        assertThatThrownBy(() -> ProductStock.create(productId, quantity))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("재고를 차감하면, 현재 재고가 요청 수량만큼 줄어든다.")
    @Test
    void deductsQuantity_whenStockIsEnough() {
        // arrange
        ProductStock productStock = ProductStock.create(1L, 10);
        int quantity = 2;

        // act
        productStock.deduct(quantity);

        // assert
        assertThat(productStock.getQuantity()).isEqualTo(8);
    }

    @DisplayName("재고보다 큰 수량을 차감하면, CONFLICT 예외를 던지고 재고를 유지한다.")
    @Test
    void throwsConflict_whenDeductQuantityIsGreaterThanStock() {
        // arrange
        ProductStock productStock = ProductStock.create(1L, 10);
        int quantity = 11;

        // act & assert
        assertAll(
            () -> assertThatThrownBy(() -> productStock.deduct(quantity))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT),
            () -> assertThat(productStock.getQuantity()).isEqualTo(10)
        );
    }

    @DisplayName("차감 수량이 0 이하이면, BAD_REQUEST 예외를 던지고 재고를 유지한다.")
    @Test
    void throwsBadRequest_whenDeductQuantityIsNotPositive() {
        // arrange
        ProductStock productStock = ProductStock.create(1L, 10);
        int quantity = 0;

        // act & assert
        assertAll(
            () -> assertThatThrownBy(() -> productStock.deduct(quantity))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST),
            () -> assertThat(productStock.getQuantity()).isEqualTo(10)
        );
    }
}
