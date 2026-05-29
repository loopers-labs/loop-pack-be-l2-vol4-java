package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenAllFieldsAreValid() {
            // act
            ProductModel product = new ProductModel(1L, "신발", "편한 신발", 10000L, 5);

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("신발"),
                () -> assertThat(product.getDescription()).isEqualTo("편한 신발"),
                () -> assertThat(product.getPrice()).isEqualTo(10000L),
                () -> assertThat(product.getStock()).isEqualTo(5)
            );
        }

        @DisplayName("brandId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(null, "신발", "편한 신발", 10000L, 5);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(1L, "  ", "편한 신발", 10000L, 5);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(1L, "신발", "편한 신발", -1L, 5);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(1L, "신발", "편한 신발", 10000L, -1);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 값이 주어지면, 값이 변경된다.")
        @Test
        void updatesProduct_whenValuesAreValid() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "편한 신발", 10000L, 5);

            // act
            product.update(2L, "구두", "멋진 구두", 20000L, 3);

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(2L),
                () -> assertThat(product.getName()).isEqualTo("구두"),
                () -> assertThat(product.getPrice()).isEqualTo(20000L),
                () -> assertThat(product.getStock()).isEqualTo(3)
            );
        }

        @DisplayName("brandId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "편한 신발", 10000L, 5);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.update(null, "구두", "멋진 구두", 20000L, 3);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
