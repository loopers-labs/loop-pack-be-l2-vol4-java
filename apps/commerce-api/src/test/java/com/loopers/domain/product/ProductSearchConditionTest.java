package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductSearchConditionTest {

    @DisplayName("상품 검색 조건을 만들 때,")
    @Nested
    class Create {

        @DisplayName("값이 없으면 기본 페이지와 최신순 정렬을 사용한다.")
        @Test
        void usesDefaultValues_whenValuesAreNull() {
            // act
            ProductSearchCondition condition = ProductSearchCondition.of(null, null, null, null, null);

            // assert
            assertAll(
                () -> assertThat(condition.sortType()).isEqualTo(ProductSortType.LATEST),
                () -> assertThat(condition.page()).isZero(),
                () -> assertThat(condition.size()).isEqualTo(20)
            );
        }

        @DisplayName("가격 내림차순 정렬 값이 주어지면, 가격 내림차순 정렬 조건을 사용한다.")
        @Test
        void usesPriceDescSort_whenPriceDescIsProvided() {
            // act
            ProductSearchCondition condition = ProductSearchCondition.of(null, "price", "desc", 0, 20);

            // assert
            assertAll(
                () -> assertThat(condition.sortType()).isEqualTo(ProductSortType.PRICE),
                () -> assertThat(condition.sortDirection()).isEqualTo(ProductSortDirection.DESC)
            );
        }

        @DisplayName("가격 정렬 방향이 없으면, 가격 오름차순 정렬 조건을 사용한다.")
        @Test
        void usesPriceAscSort_whenDirectionIsNull() {
            // act
            ProductSearchCondition condition = ProductSearchCondition.of(null, "price", null, 0, 20);

            // assert
            assertAll(
                () -> assertThat(condition.sortType()).isEqualTo(ProductSortType.PRICE),
                () -> assertThat(condition.sortDirection()).isEqualTo(ProductSortDirection.ASC)
            );
        }

        @DisplayName("지원하지 않는 정렬 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSortIsInvalid() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                ProductSearchCondition.of(null, "unknown", null, 0, 20);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("좋아요순 정렬은 아직 지원하지 않으므로, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLikesDescIsProvided() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                ProductSearchCondition.of(null, "likes", "desc", 0, 20);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("페이지 번호가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPageIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                ProductSearchCondition.of(null, "latest", null, -1, 20);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("페이지 크기가 100을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSizeIsTooLarge() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                ProductSearchCondition.of(null, "latest", null, 0, 101);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
