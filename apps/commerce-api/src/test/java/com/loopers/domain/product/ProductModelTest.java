package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.fixture.BrandFixture;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = BrandFixture.createModel();
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성 시, 상품이 반환된다.")
        @Test
        void returnsProduct_whenValidInput() {
            // act
            ProductModel product = new ProductModel(brand, "에어맥스 90", "나이키 러닝화", 150_000L);

            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo("에어맥스 90"),
                () -> assertThat(product.getDescription()).isEqualTo("나이키 러닝화"),
                () -> assertThat(product.getPrice()).isEqualTo(150_000L),
                () -> assertThat(product.getBrand()).isEqualTo(brand),
                () -> assertThat(product.getLikeCount()).isZero(),
                () -> assertThat(product.isLikesPurged()).isFalse(),
                () -> assertThat(product.getDeletedAt()).isNull()
            );
        }

        @DisplayName("상품명이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        void throwsBadRequest_whenNameIsBlank(String name) {
            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                new ProductModel(brand, name, "설명", 10_000L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 설명이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        void throwsBadRequest_whenDescriptionIsBlank(String description) {
            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                new ProductModel(brand, "상품명", description, 10_000L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                new ProductModel(brand, "상품명", "설명", -1L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정 시, 필드가 변경된다.")
        @Test
        void updatesFields_whenValidInput() {
            // arrange
            ProductModel product = new ProductModel(brand, "기존명", "기존설명", 10_000L);

            // act
            product.update("새상품명", "새설명", 20_000L);

            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo("새상품명"),
                () -> assertThat(product.getDescription()).isEqualTo("새설명"),
                () -> assertThat(product.getPrice()).isEqualTo(20_000L)
            );
        }

        @DisplayName("수정 시 상품명이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        void throwsBadRequest_whenNameIsBlank(String name) {
            // arrange
            ProductModel product = new ProductModel(brand, "기존명", "기존설명", 10_000L);

            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                product.update(name, "새설명", 20_000L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요 수를 변경할 때,")
    @Nested
    class LikeCount {

        @DisplayName("incrementLikeCount 호출 시, likeCount가 1 증가한다.")
        @Test
        void increments_whenIncrement() {
            ProductModel product = new ProductModel(brand, "상품", "설명", 10_000L);
            product.incrementLikeCount();
            assertThat(product.getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("decrementLikeCount 호출 시, likeCount가 1 감소한다.")
        @Test
        void decrements_whenDecrement() {
            ProductModel product = new ProductModel(brand, "상품", "설명", 10_000L);
            product.incrementLikeCount();
            product.incrementLikeCount();

            product.decrementLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("likeCount가 0일 때 decrementLikeCount 호출 시, 음수가 되지 않는다.")
        @Test
        void doesNotGoNegative_whenDecrementAtZero() {
            ProductModel product = new ProductModel(brand, "상품", "설명", 10_000L);
            product.decrementLikeCount();
            assertThat(product.getLikeCount()).isZero();
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("delete 호출 시, deletedAt이 기록된다.")
        @Test
        void setsDeletedAt_whenDelete() {
            ProductModel product = new ProductModel(brand, "상품", "설명", 10_000L);
            product.delete();
            assertThat(product.getDeletedAt()).isNotNull();
        }
    }
}
