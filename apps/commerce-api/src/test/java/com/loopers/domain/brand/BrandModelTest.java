package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandModelTest {

    private static final String VALID_NAME = "Loopers";
    private static final String VALID_DESCRIPTION = "감성 라이프스타일 브랜드";

    @DisplayName("브랜드 모델 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 이름과 설명을 입력하면 브랜드가 정상 생성된다")
        @Test
        void createsBrand_whenAllFieldsAreValid() {
            // given
            // when
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // then
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(brand.getDescription()).isEqualTo(VALID_DESCRIPTION)
            );
        }

        @DisplayName("이름이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new BrandModel(null, VALID_DESCRIPTION)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new BrandModel("   ", VALID_DESCRIPTION)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenDescriptionIsNull() {
            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new BrandModel(VALID_NAME, null)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenDescriptionIsBlank() {
            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new BrandModel(VALID_NAME, "")
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
