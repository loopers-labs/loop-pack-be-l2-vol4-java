package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보를 주면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenValidInfoIsProvided() {
            Long brandId = 1L;
            String name = "클래식 티셔츠";
            BigDecimal price = BigDecimal.valueOf(29000);

            Product product = new Product(brandId, name, price);

            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(brandId),
                () -> assertThat(product.getName()).isEqualTo(name),
                () -> assertThat(product.getPrice()).isEqualByComparingTo(price)
            );
        }

        @DisplayName("브랜드 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(null, "상품", BigDecimal.valueOf(1000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(1L, "  ", BigDecimal.valueOf(1000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(1L, null, BigDecimal.valueOf(1000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(1L, "상품", BigDecimal.valueOf(-1)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 0이면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenPriceIsZero() {
            Product product = new Product(1L, "무료 상품", BigDecimal.ZERO);
            assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @DisplayName("가격이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(1L, "상품", null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 정보를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 정보로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesProduct_whenValidInfoIsProvided() {
            Product product = new Product(1L, "원래 상품", BigDecimal.valueOf(10000));

            product.update(2L, "수정 상품", BigDecimal.valueOf(20000));

            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(2L),
                () -> assertThat(product.getName()).isEqualTo("수정 상품"),
                () -> assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000))
            );
        }

        @DisplayName("상품명이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUpdateNameIsBlank() {
            Product product = new Product(1L, "상품", BigDecimal.valueOf(1000));
            CoreException ex = assertThrows(CoreException.class,
                () -> product.update(1L, "", BigDecimal.valueOf(1000)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUpdatePriceIsNegative() {
            Product product = new Product(1L, "상품", BigDecimal.valueOf(1000));
            CoreException ex = assertThrows(CoreException.class,
                () -> product.update(1L, "상품", BigDecimal.valueOf(-100)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
