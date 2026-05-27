package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    @DisplayName("Product 객체를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 각 필드가 올바르게 저장된다.")
        @Test
        void createsProductModel_whenAllFieldsAreValid() {
            // arrange
            String name = "에어맥스";
            String description = "나이키 운동화";
            Long price = 150000L;
            Long brandId = 1L;

            // act
            ProductModel product = new ProductModel(name, description, price, brandId);

            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo(name),
                () -> assertThat(product.getDescription()).isEqualTo(description),
                () -> assertThat(product.getPrice()).isEqualTo(price),
                () -> assertThat(product.getBrandId()).isEqualTo(brandId)
            );
        }

        @DisplayName("가격이 0이면, 정상 생성된다.")
        @Test
        void createsProductModel_whenPriceIsZero() {
            // act & assert
            assertDoesNotThrow(() -> new ProductModel("에어맥스", "나이키 운동화", 0L, 1L));
        }

        @DisplayName("상품명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(null, "나이키 운동화", 150000L, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("", "나이키 운동화", 150000L, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("   ", "나이키 운동화", 150000L, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", null, 150000L, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", "", 150000L, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel("에어맥스", "   ", 150000L, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("brandId가 null이면, 정상 생성된다.")
        @Test
        void createsProductModel_whenBrandIdIsNull() {
            // act & assert
            assertDoesNotThrow(() -> new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
        }

        @DisplayName("생성 시 likeCount는 0으로 초기화된다.")
        @Test
        void initializesLikeCountToZero_whenCreated() {
            // act
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, null);

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0L);
        }
    }

    @DisplayName("상품 정보를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 name, description, price이면, 각 필드가 변경된다.")
        @Test
        void updatesFields_whenAllFieldsAreValid() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "나이키 운동화", 150000L, 1L);

            // act
            product.update("조던1", "나이키 농구화", 200000L);

            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo("조던1"),
                () -> assertThat(product.getDescription()).isEqualTo("나이키 농구화"),
                () -> assertThat(product.getPrice()).isEqualTo(200000L)
            );
        }
    }
}
