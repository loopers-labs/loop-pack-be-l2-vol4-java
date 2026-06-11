package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandModelTest {

    @DisplayName("브랜드 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상적으로 생성된다.")
        @Test
        void createsBrandModel() {
            // given
            String name = "Nike";

            // when
            BrandModel brand = new BrandModel(name);

            // then
            assertThat(brand.getName()).isEqualTo(name);
        }

        @DisplayName("브랜드명이 null 이거나 빈 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @NullAndEmptySource
        @ParameterizedTest
        void throwsBadRequest_whenNameIsNullOrEmpty(String name) {
            // when
            CoreException result = assertThrows(CoreException.class, () -> new BrandModel(name));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
