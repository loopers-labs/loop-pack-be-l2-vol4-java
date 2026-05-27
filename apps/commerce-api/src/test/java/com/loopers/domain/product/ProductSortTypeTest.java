package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class ProductSortTypeTest {

    @DisplayName("정렬 기준 문자열을 변환할 때,")
    @Nested
    class From {

        @DisplayName("허용된 문자열은 해당 정렬 타입으로 변환된다.")
        @ParameterizedTest
        @CsvSource({"latest,LATEST", "price_asc,PRICE_ASC", "likes_desc,LIKES_DESC"})
        void returnsSortType_whenValueIsAllowed(String value, ProductSortType expected) {
            // arrange & act
            ProductSortType sortType = ProductSortType.from(value);

            // assert
            assertThat(sortType).isEqualTo(expected);
        }

        @DisplayName("허용되지 않는 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"newest", "price", "unknown", ""})
        void throwsBadRequest_whenValueIsNotAllowed(String value) {
            // arrange & act & assert
            assertThatThrownBy(() -> ProductSortType.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> ProductSortType.from(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
