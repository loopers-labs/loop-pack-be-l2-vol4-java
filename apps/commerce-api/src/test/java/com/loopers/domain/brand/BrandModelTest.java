package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("domain")
class BrandModelTest {

    private static final String VALID_NAME = "나이키";
    private static final String VALID_DESCRIPTION = "스포츠 브랜드";

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보로 생성할 수 있다.")
        @Test
        void createsBrandModel_whenAllFieldsAreValid() {
            // arrange & act
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(brand.getDescription()).isEqualTo(VALID_DESCRIPTION)
            );
        }

        @DisplayName("브랜드명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () -> new BrandModel(null, VALID_DESCRIPTION));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드명이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () -> new BrandModel("   ", VALID_DESCRIPTION));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드 설명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () -> new BrandModel(VALID_NAME, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드 설명이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsBlank() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () -> new BrandModel(VALID_NAME, "   "));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 정보를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("올바른 정보로 수정할 수 있다.")
        @Test
        void updatesBrandModel_whenAllFieldsAreValid() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            brand.update("아디다스", "글로벌 스포츠 브랜드");

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("아디다스"),
                () -> assertThat(brand.getDescription()).isEqualTo("글로벌 스포츠 브랜드")
            );
        }

        @DisplayName("브랜드명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () -> brand.update(null, VALID_DESCRIPTION));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드명이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () -> brand.update("   ", VALID_DESCRIPTION));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드 설명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsNull() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () -> brand.update(VALID_NAME, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드 설명이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsBlank() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () -> brand.update(VALID_NAME, "   "));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
