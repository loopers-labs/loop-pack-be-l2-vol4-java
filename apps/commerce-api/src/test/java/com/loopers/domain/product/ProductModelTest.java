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

    private static ProductModel newProduct(int stock) {
        return new ProductModel(1L, "에어맥스", "런닝화", 100_000L, stock, "https://img.example/a.jpg");
    }

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("정상 입력이면, 생성 시 상태는 ACTIVE 이고 likeCount 는 0 이다.")
        @Test
        void createsActiveProduct_whenStockIsPositive() {
            // act
            ProductModel product = newProduct(10);

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("에어맥스"),
                () -> assertThat(product.getDescription()).isEqualTo("런닝화"),
                () -> assertThat(product.getPrice()).isEqualTo(100_000L),
                () -> assertThat(product.getStock()).isEqualTo(10),
                () -> assertThat(product.getLikeCount()).isZero(),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE)
            );
        }

        @DisplayName("재고 0 으로 생성하면, 상태는 SOLD_OUT 이다.")
        @Test
        void createsSoldOut_whenStockIsZero() {
            // act
            ProductModel product = newProduct(0);

            // assert
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
        }

        @DisplayName("브랜드 ID 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(null, "에어맥스", "런닝화", 100_000L, 10, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(1L, "  ", "런닝화", 100_000L, 10, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(1L, "에어맥스", "런닝화", -1L, 10, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new ProductModel(1L, "에어맥스", "런닝화", 100_000L, -1, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DecreaseStock {
        @DisplayName("요청 수량만큼 재고가 줄어든다.")
        @Test
        void decreasesStock_whenSufficient() {
            // arrange
            ProductModel product = newProduct(10);

            // act
            product.decreaseStock(3);

            // assert
            assertAll(
                () -> assertThat(product.getStock()).isEqualTo(7),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE)
            );
        }

        @DisplayName("재고가 0 이 되면, 상태는 SOLD_OUT 으로 전이한다.")
        @Test
        void transitionsToSoldOut_whenStockBecomesZero() {
            // arrange
            ProductModel product = newProduct(5);

            // act
            product.decreaseStock(5);

            // assert
            assertAll(
                () -> assertThat(product.getStock()).isZero(),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT)
            );
        }

        @DisplayName("재고보다 많이 차감 요청하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenInsufficient() {
            // arrange
            ProductModel product = newProduct(2);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.decreaseStock(3)
            );

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(product.getStock()).isEqualTo(2)
            );
        }

        @DisplayName("수량이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            // arrange
            ProductModel product = newProduct(5);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.decreaseStock(0)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 증가시킬 때, ")
    @Nested
    class IncreaseStock {
        @DisplayName("요청 수량만큼 재고가 늘어난다.")
        @Test
        void increasesStock() {
            // arrange
            ProductModel product = newProduct(2);

            // act
            product.increaseStock(3);

            // assert
            assertThat(product.getStock()).isEqualTo(5);
        }

        @DisplayName("SOLD_OUT 상품에 재고를 증가시키면, 상태는 ACTIVE 로 전이한다.")
        @Test
        void transitionsBackToActive_whenStockReplenished() {
            // arrange
            ProductModel product = newProduct(0);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);

            // act
            product.increaseStock(5);

            // assert
            assertAll(
                () -> assertThat(product.getStock()).isEqualTo(5),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE)
            );
        }

        @DisplayName("수량이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            // arrange
            ProductModel product = newProduct(5);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                product.increaseStock(-1)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요 카운트를 조작할 때, ")
    @Nested
    class LikeCount {
        @DisplayName("증가 시키면 1 씩 늘어난다.")
        @Test
        void increments() {
            // arrange
            ProductModel product = newProduct(5);

            // act
            product.incrementLikeCount();
            product.incrementLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(2L);
        }

        @DisplayName("감소 시키면 1 씩 줄어들고, 0 미만으로 떨어지지 않는다.")
        @Test
        void decrementsWithFloor() {
            // arrange
            ProductModel product = newProduct(5);
            product.incrementLikeCount();

            // act
            product.decrementLikeCount();
            product.decrementLikeCount(); // 이미 0 인데 추가 호출

            // assert
            assertThat(product.getLikeCount()).isZero();
        }
    }
}
