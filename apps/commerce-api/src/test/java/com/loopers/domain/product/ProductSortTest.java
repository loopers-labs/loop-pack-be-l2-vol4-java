package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductSortTest {

    @DisplayName("상품 정렬 조건을 변환할 때, ")
    @Nested
    class From {
        @DisplayName("값이 없으면 최신순을 반환한다.")
        @Test
        void returnsLatest_whenValueIsBlank() {
            // act
            ProductSort result = ProductSort.from(" ");

            // assert
            assertThat(result).isEqualTo(ProductSort.LATEST);
        }

        @DisplayName("지원하는 정렬 값이면, 해당 정렬 조건을 반환한다.")
        @Test
        void returnsSort_whenValueIsSupported() {
            // act
            ProductSort result = ProductSort.from("price_asc");

            // assert
            assertThat(result).isEqualTo(ProductSort.PRICE_ASC);
        }

        @DisplayName("지원하지 않는 정렬 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsNotSupported() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                ProductSort.from("unknown");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
