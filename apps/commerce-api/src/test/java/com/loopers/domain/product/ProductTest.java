package com.loopers.domain.product;

import com.loopers.domain.product.model.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenAllFieldsAreValid() {
            // Arrange & Act
            Product product = Product.create(1L, "나이키 에어맥스", "편안한 운동화", 100_000L);

            // Assert
            assertThat(product.getBrandId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("나이키 에어맥스");
            assertThat(product.getDescription()).isEqualTo("편안한 운동화");
            assertThat(product.getPrice()).isEqualTo(100_000L);
            assertThat(product.getLikeCount()).isZero();
        }

        @DisplayName("brandId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                Product.create(null, "나이키 에어맥스", "편안한 운동화", 100_000L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                Product.create(1L, null, "편안한 운동화", 100_000L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 blank이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException result = assertThrows(CoreException.class, () ->
                Product.create(1L, "   ", "편안한 운동화", 100_000L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 50자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameExceedsMaxLength() {
            CoreException result = assertThrows(CoreException.class, () ->
                Product.create(1L, "a".repeat(51), "편안한 운동화", 100_000L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 정확히 50자이면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenNameIsExactlyMaxLength() {
            Product product = Product.create(1L, "a".repeat(50), "편안한 운동화", 100_000L);
            assertThat(product.getName()).hasSize(50);
        }

        @DisplayName("설명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                Product.create(1L, "나이키 에어맥스", null, 100_000L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 blank이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsBlank() {
            CoreException result = assertThrows(CoreException.class, () ->
                Product.create(1L, "나이키 에어맥스", "   ", 100_000L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 200자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionExceedsMaxLength() {
            CoreException result = assertThrows(CoreException.class, () ->
                Product.create(1L, "나이키 에어맥스", "a".repeat(201), 100_000L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsZero() {
            CoreException result = assertThrows(CoreException.class, () ->
                Product.create(1L, "나이키 에어맥스", "편안한 운동화", 0L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            CoreException result = assertThrows(CoreException.class, () ->
                Product.create(1L, "나이키 에어맥스", "편안한 운동화", -1L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요 수를 증가시킬 때, ")
    @Nested
    class IncrementLikeCount {

        @DisplayName("호출하면 likeCount가 1 증가한다.")
        @Test
        void incrementsLikeCount() {
            // Arrange
            Product product = Product.create(1L, "나이키 에어맥스", "편안한 운동화", 100_000L);

            // Act
            product.incrementLikeCount();

            // Assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 수를 감소시킬 때, ")
    @Nested
    class DecrementLikeCount {

        @DisplayName("likeCount가 1 이상이면, 1 감소한다.")
        @Test
        void decrementsLikeCount() {
            // Arrange
            Product product = Product.create(1L, "나이키 에어맥스", "편안한 운동화", 100_000L);
            product.incrementLikeCount();

            // Act
            product.decrementLikeCount();

            // Assert
            assertThat(product.getLikeCount()).isZero();
        }

        @DisplayName("likeCount가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLikeCountIsZero() {
            // Arrange
            Product product = Product.create(1L, "나이키 에어맥스", "편안한 운동화", 100_000L);

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                product.decrementLikeCount()
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
