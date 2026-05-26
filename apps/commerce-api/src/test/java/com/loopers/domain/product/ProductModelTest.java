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

    @DisplayName("상품 모델을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsProductModel_whenAllFieldsAreValid() {
            // arrange
            String name = "니트";
            String description = "부드러운 니트";
            Long price = 30_000L;
            Integer stock = 10;
            Long brandId = 1L;

            // act
            ProductModel product = new ProductModel(brandId, name, description, price, stock);

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(brandId),
                () -> assertThat(product.getName()).isEqualTo(name),
                () -> assertThat(product.getDescription()).isEqualTo(description),
                () -> assertThat(product.getPrice()).isEqualTo(price),
                () -> assertThat(product.getStock()).isEqualTo(stock),
                () -> assertThat(product.getLikeCount()).isZero()
            );
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNegative() {
            // arrange
            Long price = -1L;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(1L, "니트", "부드러운 니트", price, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드 ID가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandIdIsNull() {
            // arrange
            Long brandId = null;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(brandId, "니트", "부드러운 니트", 30_000L, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStockIsNegative() {
            // arrange
            Integer stock = -1;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(1L, "니트", "부드러운 니트", 30_000L, stock);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 재고를 차감할 때, ")
    @Nested
    class DeductStock {
        @DisplayName("재고가 충분하면, 요청 수량만큼 재고를 차감한다.")
        @Test
        void deductsStock_whenStockIsEnough() {
            // arrange
            ProductModel product = new ProductModel(1L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            product.deductStock(3);

            // assert
            assertThat(product.getStock()).isEqualTo(7);
        }

        @DisplayName("차감 수량이 1 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsLessThanOne() {
            // arrange
            ProductModel product = new ProductModel(1L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.deductStock(0);
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(product.getStock()).isEqualTo(10)
            );
        }

        @DisplayName("차감 수량이 재고보다 크면, CONFLICT 예외가 발생하고 재고는 변경되지 않는다.")
        @Test
        void throwsConflictException_whenQuantityIsGreaterThanStock() {
            // arrange
            ProductModel product = new ProductModel(1L, "니트", "부드러운 니트", 30_000L, 2);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.deductStock(3);
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(product.getStock()).isEqualTo(2)
            );
        }
    }

    @DisplayName("상품 좋아요 수를 변경할 때, ")
    @Nested
    class ChangeLikeCount {
        @DisplayName("좋아요 수를 1 증가시킨다.")
        @Test
        void increasesLikeCount() {
            // arrange
            ProductModel product = new ProductModel(1L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            product.increaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 수를 1 감소시킨다.")
        @Test
        void decreasesLikeCount() {
            // arrange
            ProductModel product = new ProductModel(1L, "니트", "부드러운 니트", 30_000L, 10);
            product.increaseLikeCount();

            // act
            product.decreaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isZero();
        }

        @DisplayName("좋아요 수는 음수가 될 수 없다.")
        @Test
        void keepsLikeCountZero_whenDecreaseRequestedAtZero() {
            // arrange
            ProductModel product = new ProductModel(1L, "니트", "부드러운 니트", 30_000L, 10);

            // act
            product.decreaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isZero();
        }
    }
}
