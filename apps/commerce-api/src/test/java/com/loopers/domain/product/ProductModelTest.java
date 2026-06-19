package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


class ProductModelTest {

    @DisplayName("상품 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 입력으로 상품이 생성되면 입력값이 설정된다.")
        @Test
        void createsProductModel_withGivenValues() {
            // given
            Long brandId = 1L;
            String name = "테스트 상품";
            BigDecimal price = BigDecimal.valueOf(1000);

            // when
            ProductModel product = new ProductModel(brandId, name, price);

            // then
            assertAll(
                    () -> assertThat(product.getBrandId()).isEqualTo(brandId),
                    () -> assertThat(product.getName()).isEqualTo(name),
                    () -> assertThat(product.getPrice()).isEqualByComparingTo(price)
            );
        }

        @DisplayName("상품명이 null 이거나 빈 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @ParameterizedTest
        void throwsBadRequest_whenNameIsNullOrBlank(String name) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new ProductModel(1L, name, BigDecimal.valueOf(1000)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenPriceIsNull(BigDecimal price) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new ProductModel(1L, "테스트 상품", price));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @ValueSource(longs = {-1L, -1000L})
        @ParameterizedTest
        void throwsBadRequest_whenPriceIsNegative(long price) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new ProductModel(1L, "테스트 상품", BigDecimal.valueOf(price)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 모델을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("정상 입력으로 상품명과 가격이 수정된다.")
        @Test
        void updatesProductModel_withNewNameAndPrice() {
            // given
            ProductModel product = new ProductModel(1L, "기존 상품", BigDecimal.valueOf(1000));

            // when
            product.update("수정 상품", BigDecimal.valueOf(2000));

            // then
            assertAll(
                    () -> assertThat(product.getName()).isEqualTo("수정 상품"),
                    () -> assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(2000))
            );
        }

        @DisplayName("수정할 상품명이 null 이거나 빈 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @ParameterizedTest
        void throwsBadRequest_whenNewNameIsNullOrBlank(String newName) {
            // given
            ProductModel product = new ProductModel(1L, "기존 상품", BigDecimal.valueOf(1000));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> product.update(newName, BigDecimal.valueOf(2000)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수정할 가격이 null이면 BAD_REQUEST 예외가 발생한다.")
        @NullSource
        @ParameterizedTest
        void throwsBadRequest_whenNewPriceIsNull(BigDecimal newPrice) {
            // given
            ProductModel product = new ProductModel(1L, "기존 상품", BigDecimal.valueOf(1000));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> product.update("수정 상품", newPrice));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수정할 가격이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @ValueSource(longs = {-1L, -1000L})
        @ParameterizedTest
        void throwsBadRequest_whenNewPriceIsNegative(long newPrice) {
            // given
            ProductModel product = new ProductModel(1L, "기존 상품", BigDecimal.valueOf(1000));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> product.update("수정 상품", BigDecimal.valueOf(newPrice)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 모델을 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("상품이 소프트 삭제되면 deletedAt이 설정된다.")
        @Test
        void softDeletesProduct_whenDeleteIsCalled() {
            // given
            ProductModel product = new ProductModel(1L, "상품", BigDecimal.valueOf(1000));

            // when
            product.delete();

            // then
            assertThat(product.getDeletedAt()).isNotNull();
        }
    }
}
