package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("domain")
class ProductModelTest {

    private static final Long VALID_BRAND_ID = 1L;
    private static final String VALID_NAME = "나이키 에어맥스";
    private static final String VALID_DESCRIPTION = "편안한 운동화";
    private static final Long VALID_PRICE = 100_000L;
    private static final Integer VALID_STOCK = 10;

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보로 생성할 수 있다.")
        @Test
        void createsProductModel_whenAllFieldsAreValid() {
            // arrange & act
            ProductModel product = new ProductModel(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK);

            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(product.getDescription()).isEqualTo(VALID_DESCRIPTION),
                () -> assertThat(product.getPrice()).isEqualTo(VALID_PRICE),
                () -> assertThat(product.getStock()).isEqualTo(VALID_STOCK)
            );
        }

        @DisplayName("상품명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class,
                () -> new ProductModel(VALID_BRAND_ID, "   ", VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 0이면, 정상적으로 생성할 수 있다.")
        @Test
        void createsProductModel_whenPriceIsZero() {
            // arrange & act
            ProductModel product = new ProductModel(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, 0L, VALID_STOCK);

            // assert
            assertThat(product.getPrice()).isEqualTo(0L);
        }
    }

    @DisplayName("상품 정보를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("상품명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            ProductModel product = new ProductModel(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> product.update("   ", VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
