package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    @DisplayName("Product 객체를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상 생성된다.")
        @Test
        void createsProductModel_whenAllFieldsAreValid() {
            // act & assert
            assertDoesNotThrow(() -> new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, 1L));
        }

        @DisplayName("가격이 0이면, 정상 생성된다.")
        @Test
        void createsProductModel_whenPriceIsZero() {
            // act & assert
            assertDoesNotThrow(() -> new ProductModel("에어맥스", "나이키 운동화", 0L, 100, 1L));
        }

        @DisplayName("재고가 0이면, 정상 생성된다.")
        @Test
        void createsProductModel_whenStockIsZero() {
            // act & assert
            assertDoesNotThrow(() -> new ProductModel("에어맥스", "나이키 운동화", 150000L, 0, 1L));
        }

        @DisplayName("상품명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(null, "나이키 운동화", 150000L, 100, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("", "나이키 운동화", 150000L, 100, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("   ", "나이키 운동화", 150000L, 100, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", null, 150000L, 100, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", "", 150000L, 100, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", "   ", 150000L, 100, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", "나이키 운동화", null, 100, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", "나이키 운동화", -1L, 100, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", "나이키 운동화", 150000L, null, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", "나이키 운동화", 150000L, -1, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("brandId가 null이면, 정상 생성된다.")
        @Test
        void createsProductModel_whenBrandIdIsNull() {
            // act & assert
            assertDoesNotThrow(() -> new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DecreaseStock {

        @DisplayName("재고가 충분하면, 요청 수량만큼 차감된다.")
        @Test
        void decreasesStock_whenStockIsSufficient() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 10, 1L);

            // act
            product.decreaseStock(3);

            // assert
            assertThat(product.getStock()).isEqualTo(7);
        }

        @DisplayName("재고가 요청 수량과 정확히 같으면, 재고가 0이 된다.")
        @Test
        void decreasesStockToZero_whenStockEqualsQuantity() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 5, 1L);

            // act
            product.decreaseStock(5);

            // assert
            assertThat(product.getStock()).isEqualTo(0);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 3, 1L);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.decreaseStock(5)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 10, 1L);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.decreaseStock(0)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 10, 1L);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.decreaseStock(-1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 0인 상태에서 차감을 시도하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsZero() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 0, 1L);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.decreaseStock(1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
