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

    private static ProductModel validProduct(int stock) {
        return new ProductModel(1L, "상품명", "상품 설명", 10000L, stock, "image.jpg");
    }

    @DisplayName("상품 모델을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("올바른 정보를 입력하면 성공한다.")
        @Test
        void creates_successfully_with_valid_inputs() {
            ProductModel product = validProduct(10);

            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("상품명"),
                () -> assertThat(product.getPrice()).isEqualTo(10000L),
                () -> assertThat(product.getStock()).isEqualTo(10),
                () -> assertThat(product.isSoldOut()).isFalse()
            );
        }

        @DisplayName("브랜드 ID가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_brandId_is_null() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new ProductModel(null, "상품명", "설명", 10000L, 10, "image.jpg"));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_name_is_blank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new ProductModel(1L, "  ", "설명", 10000L, 10, "image.jpg"));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 설명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_description_is_blank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new ProductModel(1L, "상품명", "", 10000L, 10, "image.jpg"));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_price_is_negative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new ProductModel(1L, "상품명", "설명", -1L, 10, "image.jpg"));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_stock_is_negative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new ProductModel(1L, "상품명", "설명", 10000L, -1, "image.jpg"));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DeductStock {

        @DisplayName("충분한 재고가 있으면 차감에 성공한다.")
        @Test
        void deducts_stock_successfully_when_stock_is_sufficient() {
            ProductModel product = validProduct(10);

            product.deductStock(3);

            assertThat(product.getStock()).isEqualTo(7);
        }

        @DisplayName("재고가 0이 되면 soldOutAt이 설정된다.")
        @Test
        void marks_sold_out_when_stock_reaches_zero() {
            ProductModel product = validProduct(1);

            product.deductStock(1);

            assertAll(
                () -> assertThat(product.getStock()).isEqualTo(0),
                () -> assertThat(product.isSoldOut()).isTrue(),
                () -> assertThat(product.getSoldOutAt()).isNotNull()
            );
        }

        @DisplayName("재고보다 많은 수량을 차감하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_quantity_exceeds_stock() {
            ProductModel product = validProduct(2);

            CoreException ex = assertThrows(CoreException.class,
                () -> product.deductStock(3));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요 수를 증가시킬 때,")
    @Nested
    class IncrementLikeCount {

        @DisplayName("likeCount 가 1 증가한다.")
        @Test
        void increments_like_count_by_one() {
            ProductModel product = validProduct(10);

            product.incrementLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(1L);
        }
    }

    @DisplayName("좋아요 수를 감소시킬 때,")
    @Nested
    class DecrementLikeCount {

        @DisplayName("likeCount 가 1 감소한다.")
        @Test
        void decrements_like_count_by_one() {
            ProductModel product = validProduct(10);
            product.incrementLikeCount();

            product.decrementLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(0L);
        }

        @DisplayName("likeCount 가 0 이하로 내려가지 않는다.")
        @Test
        void does_not_go_below_zero() {
            ProductModel product = validProduct(10);

            product.decrementLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(0L);
        }
    }

    @DisplayName("재고를 복구할 때,")
    @Nested
    class RestoreStock {

        @DisplayName("재고가 복구되면 품절이 해제된다.")
        @Test
        void clears_sold_out_when_stock_is_restored() {
            ProductModel product = validProduct(1);
            product.deductStock(1); // 품절 상태로

            product.restoreStock(5);

            assertAll(
                () -> assertThat(product.getStock()).isEqualTo(5),
                () -> assertThat(product.isSoldOut()).isFalse(),
                () -> assertThat(product.getSoldOutAt()).isNull()
            );
        }

        @DisplayName("재고를 복구해도 stock이 0이면 품절 상태가 유지된다.")
        @Test
        void keeps_sold_out_when_stock_remains_zero_after_restore() {
            ProductModel product = validProduct(1);
            product.deductStock(1); // 품절 상태로

            product.restoreStock(0);

            assertThat(product.isSoldOut()).isTrue();
        }
    }
}