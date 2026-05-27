package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    private static final String VALID_NAME = "나이키 에어맥스";
    private static final int VALID_PRICE = 150_000;
    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = new BrandModel("Nike", "스포츠 브랜드");
    }

    @DisplayName("ProductModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성 시 필드가 정상 설정된다.")
        @Test
        void createsProductModel_whenAllFieldsAreValid() {
            // arrange & act
            ProductModel product = new ProductModel(brand, VALID_NAME, VALID_PRICE);

            // assert
            assertAll(
                () -> assertThat(product.getBrand()).isEqualTo(brand),
                () -> assertThat(product.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(product.getPrice()).isEqualTo(VALID_PRICE),
                () -> assertThat(product.isDeleted()).isFalse()
            );
        }

        @DisplayName("null 브랜드로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(null, VALID_NAME, VALID_PRICE)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null 상품명으로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(brand, null, VALID_PRICE)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백 상품명으로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(brand, "  ", VALID_PRICE)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsZero() {
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(brand, VALID_NAME, 0)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(brand, VALID_NAME, -1)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("update()를 호출할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정 시 이름과 가격이 갱신되고 브랜드는 변경되지 않는다.")
        @Test
        void updatesNameAndPrice_brandUnchanged() {
            // arrange
            ProductModel product = new ProductModel(brand, VALID_NAME, VALID_PRICE);

            // act
            product.update("아디다스 울트라부스트", 200_000);

            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo("아디다스 울트라부스트"),
                () -> assertThat(product.getPrice()).isEqualTo(200_000),
                () -> assertThat(product.getBrand()).isEqualTo(brand) // 브랜드 불변
            );
        }

        @DisplayName("공백 이름으로 수정 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            ProductModel product = new ProductModel(brand, VALID_NAME, VALID_PRICE);
            CoreException result = assertThrows(CoreException.class, () ->
                product.update("  ", VALID_PRICE)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("0 이하의 가격으로 수정 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNotPositive() {
            ProductModel product = new ProductModel(brand, VALID_NAME, VALID_PRICE);
            CoreException result = assertThrows(CoreException.class, () ->
                product.update(VALID_NAME, 0)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("isDeleted()를 호출할 때,")
    @Nested
    class IsDeleted {

        @DisplayName("삭제되지 않은 상품은 false를 반환한다.")
        @Test
        void returnsFalse_whenProductIsNotDeleted() {
            ProductModel product = new ProductModel(brand, VALID_NAME, VALID_PRICE);
            assertThat(product.isDeleted()).isFalse();
        }

        @DisplayName("delete() 호출 후 true를 반환한다.")
        @Test
        void returnsTrue_afterDeleteCalled() {
            ProductModel product = new ProductModel(brand, VALID_NAME, VALID_PRICE);
            product.delete();
            assertThat(product.isDeleted()).isTrue();
        }
    }
}
