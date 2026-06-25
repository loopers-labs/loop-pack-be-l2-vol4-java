package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    private static final String VALID_NAME = "나이키";
    private static final String VALID_DESCRIPTION = "Just Do It";

    @DisplayName("Brand 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("이름과 설명이 유효하면, Brand 가 정상적으로 생성된다.")
        @Test
        void createsBrand_whenInputsAreValid() {
            // act
            Brand brand = new Brand(VALID_NAME, VALID_DESCRIPTION);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(brand.getDescription()).isEqualTo(VALID_DESCRIPTION)
            );
        }

        @DisplayName("설명은 선택값이라, null 이어도 정상적으로 생성된다.")
        @Test
        void createsBrand_whenDescriptionIsNull() {
            // act
            Brand brand = new Brand(VALID_NAME, null);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(brand.getDescription()).isNull()
            );
        }

        @DisplayName("이름이 null 이거나 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "   "})
        void throwsBadRequest_whenNameIsBlank(String invalidName) {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> new Brand(invalidName, VALID_DESCRIPTION));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
