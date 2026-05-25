package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    private static final String VALID_NAME = "감성 후드";
    private static final String VALID_DESCRIPTION = "포근한 가을 후드티";
    private static final Long VALID_PRICE = 49_000L;

    private static BrandModel validBrand() {
        return new BrandModel("Loopers", "감성 라이프스타일 브랜드");
    }

    @DisplayName("상품 모델 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 모든 필드를 입력하면 상품이 정상 생성되고 좋아요 수는 0으로 초기화된다")
        @Test
        void createsProduct_whenAllFieldsAreValid() {
            // when
            ProductModel product = new ProductModel(validBrand(), VALID_NAME, VALID_DESCRIPTION, VALID_PRICE);

            // then
            assertAll(
                () -> assertThat(product.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(product.getDescription()).isEqualTo(VALID_DESCRIPTION),
                () -> assertThat(product.getPrice()).isEqualTo(VALID_PRICE),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }

        @DisplayName("브랜드가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenBrandIsNull() {
            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new ProductModel(null, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNameIsNullOrBlank() {
            // when
            CoreException nullEx = assertThrows(CoreException.class, () ->
                new ProductModel(validBrand(), null, VALID_DESCRIPTION, VALID_PRICE)
            );
            CoreException blankEx = assertThrows(CoreException.class, () ->
                new ProductModel(validBrand(), "  ", VALID_DESCRIPTION, VALID_PRICE)
            );

            // then
            assertAll(
                () -> assertThat(nullEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(blankEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }

        @DisplayName("설명이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenDescriptionIsNullOrBlank() {
            // when
            CoreException nullEx = assertThrows(CoreException.class, () ->
                new ProductModel(validBrand(), VALID_NAME, null, VALID_PRICE)
            );
            CoreException blankEx = assertThrows(CoreException.class, () ->
                new ProductModel(validBrand(), VALID_NAME, "", VALID_PRICE)
            );

            // then
            assertAll(
                () -> assertThat(nullEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(blankEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }

        @DisplayName("가격이 null이거나 음수이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPriceIsNullOrNegative() {
            // when
            CoreException nullEx = assertThrows(CoreException.class, () ->
                new ProductModel(validBrand(), VALID_NAME, VALID_DESCRIPTION, null)
            );
            CoreException negativeEx = assertThrows(CoreException.class, () ->
                new ProductModel(validBrand(), VALID_NAME, VALID_DESCRIPTION, -1L)
            );

            // then
            assertAll(
                () -> assertThat(nullEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(negativeEx.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

    @DisplayName("좋아요 수 증가 시")
    @Nested
    class IncreaseLike {

        @DisplayName("호출할 때마다 좋아요 수가 1씩 증가한다")
        @Test
        void incrementsLikeCount_byOne() {
            // given
            ProductModel product = new ProductModel(validBrand(), VALID_NAME, VALID_DESCRIPTION, VALID_PRICE);

            // when
            product.increaseLike();
            product.increaseLike();

            // then
            assertThat(product.getLikeCount()).isEqualTo(2);
        }
    }

    @DisplayName("좋아요 수 감소 시")
    @Nested
    class DecreaseLike {

        @DisplayName("좋아요 수가 1 이상이면 1만큼 감소한다")
        @Test
        void decrementsLikeCount_byOne() {
            // given
            ProductModel product = new ProductModel(validBrand(), VALID_NAME, VALID_DESCRIPTION, VALID_PRICE);
            product.increaseLike();
            product.increaseLike();

            // when
            product.decreaseLike();

            // then
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 수가 0이면 감소해도 0으로 유지된다 (음수 방지)")
        @Test
        void staysZero_whenAlreadyZero() {
            // given
            ProductModel product = new ProductModel(validBrand(), VALID_NAME, VALID_DESCRIPTION, VALID_PRICE);

            // when
            product.decreaseLike();

            // then
            assertThat(product.getLikeCount()).isZero();
        }
    }
}
