package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("올바른 정보로 생성하면 정상적으로 생성된다.")
        @Test
        void createsProduct_whenAllFieldsAreValid() {
            // act
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("신발"),
                () -> assertThat(product.getDescription()).isEqualTo("러닝화"),
                () -> assertThat(product.getPrice()).isEqualTo(50_000L),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE)
            );
        }

        @DisplayName("브랜드 ID가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(null, "신발", "러닝화", 50_000L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void throwsBadRequest_whenNameIsBlank(String name) {
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(1L, name, "러닝화", 50_000L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void throwsBadRequest_whenDescriptionIsBlank(String description) {
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(1L, "신발", description, 50_000L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면 BAD_REQUEST 예외가 발생한다 (Money VO 검증).")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(1L, "신발", "러닝화", -1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(1L, "신발", "러닝화", null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 정보를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("이름, 설명, 가격이 변경된다.")
        @Test
        void updatesFields() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);

            // act
            product.update("러닝화 v2", "개선된 러닝화", 60_000L);

            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo("러닝화 v2"),
                () -> assertThat(product.getDescription()).isEqualTo("개선된 러닝화"),
                () -> assertThat(product.getPrice()).isEqualTo(60_000L)
            );
        }

    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("status 가 DELETED 로 변경되고 deletedAt 이 채워진다.")
        @Test
        void marksAsDeleted() {
            // arrange
            ProductModel product = new ProductModel(1L, "신발", "러닝화", 50_000L);

            // act
            product.delete();

            // assert
            assertAll(
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED),
                () -> assertThat(product.getDeletedAt()).isNotNull()
            );
        }
    }
}
