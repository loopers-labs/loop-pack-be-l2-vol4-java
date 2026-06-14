package com.loopers.brand.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandNameTest {

    @DisplayName("브랜드명을 생성할 때 ")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면, 브랜드명을 생성한다.")
        @Test
        void createsBrandName_whenValueIsValid() {
            // arrange
            String value = "애플";

            // act
            BrandName brandName = BrandName.of(value);

            // assert
            assertThat(brandName.value()).isEqualTo(value);
        }

        @DisplayName("값이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsBlank() {
            // arrange
            String blankValue = " ";

            // act & assert
            assertThatThrownBy(() -> BrandName.of(blankValue))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
