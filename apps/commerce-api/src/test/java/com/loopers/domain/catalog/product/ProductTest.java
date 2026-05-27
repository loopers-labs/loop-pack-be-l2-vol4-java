package com.loopers.domain.catalog.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("재고가 있으면 판매 가능 상태로 생성된다.")
        @Test
        void createsOnSaleProduct_whenStockIsPositive() {
            // act
            Product product = new Product(1L, "상품", "설명", 1_000L, 10);

            // assert
            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getPriceAmount()).isEqualTo(1_000L),
                () -> assertThat(product.getStockQuantity()).isEqualTo(10),
                () -> assertThat(product.getLikeCount()).isZero(),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE)
            );
        }

        @DisplayName("재고가 0이면 품절 상태로 생성된다.")
        @Test
        void createsSoldOutProduct_whenStockIsZero() {
            // act
            Product product = new Product(1L, "상품", "설명", 1_000L, 0);

            // assert
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
        }

        @DisplayName("요청 상태가 있으면 해당 상품 상태로 생성된다.")
        @Test
        void createsProductWithRequestedStatus_whenStatusIsProvided() {
            // act
            Product product = new Product(1L, "상품", "설명", 1_000L, 10, ProductStatus.SOLD_OUT);

            // assert
            assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
        }

        @DisplayName("재고가 0인데 판매 가능 상태로 생성하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenOnSaleStatusIsProvidedWithZeroStock() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Product(1L, "상품", "설명", 1_000L, 0, ProductStatus.ON_SALE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Product(1L, "상품", "설명", -1L, 10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DecreaseStock {

        @DisplayName("재고가 충분하면 요청 수량만큼 차감한다.")
        @Test
        void decreasesStock_whenStockIsEnough() {
            // arrange
            Product product = new Product(1L, "상품", "설명", 1_000L, 10);

            // act
            product.decreaseStock(3);

            // assert
            assertAll(
                () -> assertThat(product.getStockQuantity()).isEqualTo(7),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE)
            );
        }

        @DisplayName("재고가 모두 차감되면 품절 상태가 된다.")
        @Test
        void marksSoldOut_whenStockBecomesZero() {
            // arrange
            Product product = new Product(1L, "상품", "설명", 1_000L, 3);

            // act
            product.decreaseStock(3);

            // assert
            assertAll(
                () -> assertThat(product.getStockQuantity()).isZero(),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT)
            );
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStockIsNotEnough() {
            // arrange
            Product product = new Product(1L, "상품", "설명", 1_000L, 2);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.decreaseStock(3);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 판매를 중지할 때, ")
    @Nested
    class StopSelling {

        @DisplayName("상태를 STOPPED로 바꾸고 soft delete 처리한다.")
        @Test
        void marksStoppedAndDeleted_whenStopSellingIsCalled() {
            // arrange
            Product product = new Product(1L, "상품", "설명", 1_000L, 10);

            // act
            product.stopSelling();

            // assert
            assertAll(
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED),
                () -> assertThat(product.getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("판매 중지된 상품은 수정할 수 없다.")
        @Test
        void throwsBadRequestException_whenStoppedProductIsUpdated() {
            // arrange
            Product product = new Product(1L, "상품", "설명", 1_000L, 10);
            product.stopSelling();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.update("새 상품", "새 설명", 2_000L, 5);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 수정 요청 상태가 STOPPED이면 soft delete 처리한다.")
        @Test
        void marksStoppedAndDeleted_whenUpdatedStatusIsStopped() {
            // arrange
            Product product = new Product(1L, "상품", "설명", 1_000L, 10);

            // act
            product.update("상품", "설명", 1_000L, 10, ProductStatus.STOPPED);

            // assert
            assertAll(
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.STOPPED),
                () -> assertThat(product.getDeletedAt()).isNotNull()
            );
        }
    }

    @DisplayName("좋아요 수를 차감할 때, ")
    @Nested
    class DecreaseLikeCount {

        @DisplayName("0 아래로 내려가지 않는다.")
        @Test
        void doesNotDecreaseBelowZero() {
            // arrange
            Product product = new Product(1L, "상품", "설명", 1_000L, 10);

            // act
            product.decreaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isZero();
        }
    }
}
