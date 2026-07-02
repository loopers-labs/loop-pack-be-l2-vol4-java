package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {
        @DisplayName("브랜드가 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            CoreException result =
                assertThrows(CoreException.class, () -> new Product(null, "상품", "설명", 1_000L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException result =
                assertThrows(CoreException.class, () -> new Product(1L, " ", "설명", 1_000L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            CoreException result =
                assertThrows(CoreException.class, () -> new Product(1L, "상품", "설명", -1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 값이면 상품이 생성된다.")
        @Test
        void createsProduct() {
            Product product = new Product(1L, "상품", "설명", 1_000L);
            assertThat(product.getBrandId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("상품");
            assertThat(product.getPrice()).isEqualTo(1_000L);
        }
    }

    @DisplayName("상품을 수정하면 이름/설명/가격이 변경된다.")
    @Test
    void update() {
        Product product = new Product(1L, "상품", "설명", 1_000L);
        product.update("새상품", "새설명", 2_000L);
        assertThat(product.getName()).isEqualTo("새상품");
        assertThat(product.getDescription()).isEqualTo("새설명");
        assertThat(product.getPrice()).isEqualTo(2_000L);
    }
}
