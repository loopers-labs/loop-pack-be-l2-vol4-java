package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductModelTest {

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 이름/가격/브랜드ID로 생성하면, ProductModel이 정상 생성된다.")
        @Test
        void createsProduct_whenAllFieldsAreValid() {
            // arrange
            String name = "에어포스1";
            Long price = 139000L;
            Long brandId = 1L;

            // act
            ProductModel product = new ProductModel(name, price, brandId);

            // assert
            assertThat(product.getName()).isEqualTo(name);
            assertThat(product.getPrice()).isEqualTo(price);
            assertThat(product.getBrandId()).isEqualTo(brandId);
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act & assert
            assertThatThrownBy(() -> new ProductModel(null, 139000L, 1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act & assert
            assertThatThrownBy(() -> new ProductModel("   ", 139000L, 1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            // act & assert
            assertThatThrownBy(() -> new ProductModel("에어포스1", null, 1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // act & assert
            assertThatThrownBy(() -> new ProductModel("에어포스1", -1L, 1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            // act & assert
            assertThatThrownBy(() -> new ProductModel("에어포스1", 139000L, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 이름과 가격으로 수정하면, 값이 변경된다.")
        @Test
        void updatesProduct_whenNameAndPriceAreValid() {
            // arrange
            ProductModel product = new ProductModel("에어포스1", 139000L, 1L);

            // act
            product.update("에어맥스90", 159000L);

            // assert
            assertThat(product.getName()).isEqualTo("에어맥스90");
            assertThat(product.getPrice()).isEqualTo(159000L);
            assertThat(product.getBrandId()).isEqualTo(1L);
        }

        @DisplayName("이름이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            ProductModel product = new ProductModel("에어포스1", 139000L, 1L);

            // act & assert
            assertThatThrownBy(() -> product.update("   ", 139000L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // arrange
            ProductModel product = new ProductModel("에어포스1", 139000L, 1L);

            // act & assert
            assertThatThrownBy(() -> product.update("에어포스1", -1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }


}
