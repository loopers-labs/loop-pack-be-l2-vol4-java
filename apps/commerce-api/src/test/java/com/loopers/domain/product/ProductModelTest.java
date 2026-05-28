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

    private static final Long VALID_BRAND_ID = 1L;
    private static final String VALID_NAME = "감성 후드";
    private static final String VALID_DESCRIPTION = "포근한 가을 후드티";
    private static final Long VALID_PRICE = 49_000L;

    @DisplayName("상품 모델 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 모든 필드를 입력하면 상품이 정상 생성되고 좋아요 수는 0으로 초기화된다")
        @Test
        void createsProduct_whenAllFieldsAreValid() {
            ProductModel product = new ProductModel(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE);

            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(VALID_BRAND_ID),
                () -> assertThat(product.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(product.getDescription()).isEqualTo(VALID_DESCRIPTION),
                () -> assertThat(product.getPrice().value()).isEqualTo(VALID_PRICE),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }

        @DisplayName("brandId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new ProductModel(null, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNameIsNullOrBlank() {
            CoreException nullEx = assertThrows(CoreException.class, () ->
                new ProductModel(VALID_BRAND_ID, null, VALID_DESCRIPTION, VALID_PRICE)
            );
            CoreException blankEx = assertThrows(CoreException.class, () ->
                new ProductModel(VALID_BRAND_ID, "  ", VALID_DESCRIPTION, VALID_PRICE)
            );

            assertAll(
                () -> assertThat(nullEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(blankEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }

        @DisplayName("설명이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenDescriptionIsNullOrBlank() {
            CoreException nullEx = assertThrows(CoreException.class, () ->
                new ProductModel(VALID_BRAND_ID, VALID_NAME, null, VALID_PRICE)
            );
            CoreException blankEx = assertThrows(CoreException.class, () ->
                new ProductModel(VALID_BRAND_ID, VALID_NAME, "", VALID_PRICE)
            );

            assertAll(
                () -> assertThat(nullEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(blankEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }

        @DisplayName("가격이 null이거나 음수이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPriceIsNullOrNegative() {
            CoreException nullEx = assertThrows(CoreException.class, () ->
                new ProductModel(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, null)
            );
            CoreException negativeEx = assertThrows(CoreException.class, () ->
                new ProductModel(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, -1L)
            );

            assertAll(
                () -> assertThat(nullEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(negativeEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

}
