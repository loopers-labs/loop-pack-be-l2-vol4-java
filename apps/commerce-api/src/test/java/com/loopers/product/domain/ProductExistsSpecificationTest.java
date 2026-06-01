package com.loopers.product.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductExistsSpecificationTest {

    private final ProductExistsSpecification spec = new ProductExistsSpecification();

    @DisplayName("isSatisfiedBy를 호출할 때,")
    @Nested
    class IsSatisfiedBy {

        @DisplayName("상품이 존재하면, true를 반환한다.")
        @Test
        void returnsTrue_whenProductExists() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, null);

            // act & assert
            assertThat(spec.isSatisfiedBy(Optional.of(product))).isTrue();
        }

        @DisplayName("상품이 없으면, false를 반환한다.")
        @Test
        void returnsFalse_whenProductNotExists() {
            // act & assert
            assertThat(spec.isSatisfiedBy(Optional.empty())).isFalse();
        }
    }
}
