package com.loopers.product.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductListCachePropertiesTest {

    @DisplayName("캐시 가능한 최대 상품 수가 최대 페이지 크기보다 작으면 예외를 던진다.")
    @Test
    void throwsException_whenCacheableMaxItemsIsLessThanCacheableMaxSize() {
        // arrange
        int cacheableMaxSize = 50;
        int cacheableMaxItems = 20;

        // act & assert
        assertThatThrownBy(() -> new ProductListCacheProperties(
            30,
            3,
            3,
            80,
            3,
            cacheableMaxSize,
            cacheableMaxItems
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("cacheableMaxItems must be greater than or equal to cacheableMaxSize.");
    }
}
