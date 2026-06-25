package com.loopers.domain.product;

import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    private static final Long VALID_BRAND_ID = 1L;
    private static final String VALID_NAME = "에어맥스";
    private static final String VALID_DESCRIPTION = "운동화";
    private static final Long VALID_PRICE = 1000L;
    private static final Integer VALID_STOCK = 10;

    private Product product() {
        return new Product(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK);
    }

    @DisplayName("Product 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 정보가 유효하면, Product 가 정상적으로 생성된다.")
        @Test
        void createsProduct_whenAllInputsAreValid() {
            // act
            Product product = product();

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(VALID_BRAND_ID),
                () -> assertThat(product.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(product.getDescription()).isEqualTo(VALID_DESCRIPTION),
                () -> assertThat(product.getPrice()).isEqualTo(VALID_PRICE),
                () -> assertThat(product.getStock()).isEqualTo(VALID_STOCK)
            );
        }

        @DisplayName("가격과 재고가 0 이면, 정상적으로 생성된다. (경계값)")
        @Test
        void createsProduct_whenPriceAndStockAreZero() {
            // act
            Product product = new Product(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, 0L, 0);

            // assert
            assertAll(
                () -> assertThat(product.getPrice()).isEqualTo(0L),
                () -> assertThat(product.getStock()).isEqualTo(0)
            );
        }

        @DisplayName("브랜드 ID 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(null, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 null 이거나 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "   "})
        void throwsBadRequest_whenNameIsBlank(String invalidName) {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(VALID_BRAND_ID, invalidName, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 설명이 null 이거나 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " "})
        void throwsBadRequest_whenDescriptionIsBlank(String invalidDescription) {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(VALID_BRAND_ID, VALID_NAME, invalidDescription, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 null 이면, (언박싱 NPE 가 아닌) BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, null, VALID_STOCK));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 null 이면, (언박싱 NPE 가 아닌) BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, (Money 위임으로) BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, -1L, VALID_STOCK));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면, (Quantity 위임으로) BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNegative() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> new Product(VALID_BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, -1));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DecreaseStock {

        @DisplayName("재고가 충분하면, 차감된 수량이 반영된다.")
        @Test
        void decreasesStock_whenEnough() {
            // arrange
            Product product = product(); // stock 10

            // act
            product.decreaseStock(Quantity.of(3));

            // assert
            assertThat(product.getStock()).isEqualTo(7);
        }

        @DisplayName("재고와 같은 수량을 차감하면, 재고가 0 이 된다. (경계값)")
        @Test
        void decreasesToZero_whenEqual() {
            // arrange
            Product product = product(); // stock 10

            // act
            product.decreaseStock(Quantity.of(10));

            // assert
            assertThat(product.getStock()).isEqualTo(0);
        }

        @DisplayName("재고보다 많은 수량을 차감하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotEnough() {
            // arrange
            Product product = product(); // stock 10

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> product.decreaseStock(Quantity.of(11)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNull() {
            // arrange
            Product product = product();

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> product.decreaseStock(null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("정상 입력이면, 정보가 갱신되고 브랜드는 변경되지 않는다.")
        @Test
        void updatesFields_whenValid() {
            // arrange
            Product product = product();

            // act
            product.update("줌플라이", "런닝화", 2000L, 5);

            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo("줌플라이"),
                () -> assertThat(product.getDescription()).isEqualTo("런닝화"),
                () -> assertThat(product.getPrice()).isEqualTo(2000L),
                () -> assertThat(product.getStock()).isEqualTo(5),
                () -> assertThat(product.getBrandId()).isEqualTo(VALID_BRAND_ID)
            );
        }

        @DisplayName("상품명이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " "})
        void throwsBadRequest_whenNameIsBlank(String invalidName) {
            // arrange
            Product product = product();

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> product.update(invalidName, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            // arrange
            Product product = product();

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> product.update(VALID_NAME, VALID_DESCRIPTION, null, VALID_STOCK));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 복원할 때, ")
    @Nested
    class IncreaseStock {

        @DisplayName("복원 수량만큼 재고가 증가한다. (결제 실패 보상)")
        @Test
        void increasesStock() {
            // arrange
            Product product = product(); // stock 10

            // act
            product.increaseStock(Quantity.of(3));

            // assert
            assertThat(product.getStock()).isEqualTo(13);
        }

        @DisplayName("복원 수량이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNull() {
            // arrange
            Product product = product();

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> product.increaseStock(null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
