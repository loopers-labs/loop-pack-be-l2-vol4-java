package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductStockTest {

    private static final Long PRODUCT_ID = 1L;

    @Test
    @DisplayName("create 로 생성하면 productId 와 수량이 저장된다")
    void givenProductIdAndQuantity_whenCreate_thenStoresFields() {
        ProductStock stock = ProductStock.create(PRODUCT_ID, 10);

        assertAll(
                () -> assertThat(stock.getProductId()).isEqualTo(PRODUCT_ID),
                () -> assertThat(stock.getQuantity()).isEqualTo(10)
        );
    }

    @Test
    @DisplayName("초기 수량이 0 이어도 생성된다")
    void givenZeroInitialQuantity_whenCreate_thenStoresZero() {
        ProductStock stock = ProductStock.create(PRODUCT_ID, 0);

        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("productId 가 null 이면 CoreException 이 발생한다")
    void givenNullProductId_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> ProductStock.create(null, 10))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("productId 는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10})
    @DisplayName("초기 수량이 음수이면 CoreException 이 발생한다")
    void givenNegativeInitialQuantity_whenCreate_thenThrowsCoreException(int invalid) {
        assertThatThrownBy(() -> ProductStock.create(PRODUCT_ID, invalid))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("재고는 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("decrease 호출 시 잔여 재고가 차감된다")
    void givenStockOf10_whenDecrease3_thenQuantityBecomes7() {
        ProductStock stock = ProductStock.create(PRODUCT_ID, 10);

        stock.decrease(3);

        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("decrease 로 재고와 같은 수량을 차감하면 0 이 된다")
    void givenStockOf5_whenDecrease5_thenQuantityBecomesZero() {
        ProductStock stock = ProductStock.create(PRODUCT_ID, 5);

        stock.decrease(5);

        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("잔여 재고보다 많은 수량을 decrease 하면 CoreException 이 발생한다 (음수 진입 방지)")
    void givenStockOf3_whenDecrease5_thenThrowsCoreException() {
        ProductStock stock = ProductStock.create(PRODUCT_ID, 3);

        assertThatThrownBy(() -> stock.decrease(5))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("재고가 부족합니다.");
        assertThat(stock.getQuantity()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    @DisplayName("decrease 에 0 이하 수량을 주면 CoreException 이 발생한다")
    void givenNonPositiveQuantity_whenDecrease_thenThrowsCoreException(int invalid) {
        ProductStock stock = ProductStock.create(PRODUCT_ID, 10);

        assertThatThrownBy(() -> stock.decrease(invalid))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("차감 수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("increase 호출 시 잔여 재고가 증가한다 (보상)")
    void givenStockOf5_whenIncrease3_thenQuantityBecomes8() {
        ProductStock stock = ProductStock.create(PRODUCT_ID, 5);

        stock.increase(3);

        assertThat(stock.getQuantity()).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    @DisplayName("increase 에 0 이하 수량을 주면 CoreException 이 발생한다")
    void givenNonPositiveQuantity_whenIncrease_thenThrowsCoreException(int invalid) {
        ProductStock stock = ProductStock.create(PRODUCT_ID, 10);

        assertThatThrownBy(() -> stock.increase(invalid))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("증가 수량은 1 이상이어야 합니다.");
    }
}
