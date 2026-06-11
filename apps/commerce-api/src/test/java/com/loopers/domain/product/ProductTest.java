package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    private static final Long BRAND_ID = 1L;
    private static final String NAME = "에어맥스";
    private static final Money PRICE = Money.of(199_000L);

    @DisplayName("Product 를 create 로 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 값이면 id 는 미할당(0), deleted 는 false 인 신규 Product 가 생성된다.")
        @Test
        void createsNewProduct_whenValid() {
            // act
            Product product = Product.create(BRAND_ID, NAME, PRICE);

            // assert
            assertThat(product.getId()).isEqualTo(0L);
            assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
            assertThat(product.getName()).isEqualTo(NAME);
            assertThat(product.getPrice()).isEqualTo(PRICE);
            assertThat(product.isDeleted()).isFalse();
        }

        @DisplayName("brandId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> Product.create(null, NAME, PRICE));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name 이 null 이거나 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "\t"})
        void throwsBadRequest_whenNameIsBlank(String invalidName) {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> Product.create(BRAND_ID, invalidName, PRICE));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> Product.create(BRAND_ID, NAME, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Product 를 modify 로 수정할 때, ")
    @Nested
    class Modify {

        @DisplayName("정상 값이면 name 과 price 가 변경되고 brandId 는 그대로 유지된다.")
        @Test
        void modifiesNameAndPrice() {
            // arrange
            Product product = Product.create(BRAND_ID, NAME, PRICE);
            Money newPrice = Money.of(150_000L);

            // act
            product.modify("코르테즈", newPrice);

            // assert
            assertThat(product.getName()).isEqualTo("코르테즈");
            assertThat(product.getPrice()).isEqualTo(newPrice);
            assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
        }

        @DisplayName("name 이 null 이거나 공백이면 BAD_REQUEST 예외가 발생하고 기존 값이 유지된다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " "})
        void throwsBadRequest_whenNameIsBlank(String invalidName) {
            // arrange
            Product product = Product.create(BRAND_ID, NAME, PRICE);

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> product.modify(invalidName, Money.of(1L)));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(product.getName()).isEqualTo(NAME);
            assertThat(product.getPrice()).isEqualTo(PRICE);
        }

        @DisplayName("price 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            // arrange
            Product product = Product.create(BRAND_ID, NAME, PRICE);

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> product.modify("다른이름", null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Product 를 delete 로 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("deleted 상태가 true 로 변경된다.")
        @Test
        void marksAsDeleted() {
            // arrange
            Product product = Product.create(BRAND_ID, NAME, PRICE);

            // act
            product.delete();

            // assert
            assertThat(product.isDeleted()).isTrue();
        }

        @DisplayName("delete 를 두 번 호출해도 deleted 는 true 그대로 유지된다 (멱등).")
        @Test
        void isIdempotent_whenCalledTwice() {
            // arrange
            Product product = Product.create(BRAND_ID, NAME, PRICE);
            product.delete();

            // act
            product.delete();

            // assert
            assertThat(product.isDeleted()).isTrue();
        }
    }
}
