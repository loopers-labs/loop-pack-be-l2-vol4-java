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

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("이름과 설명이 모두 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenNameAndDescriptionProvided() {
            // arrange
            String name = "나이키";
            String description = "스포츠 브랜드";

            // act
            BrandModel brand = new BrandModel(name, description);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("설명이 빈 문자열이라도 정상적으로 생성된다.")
        @Test
        void createsBrand_whenDescriptionIsEmpty() {
            // arrange
            String description = "";

            // act
            BrandModel brand = new BrandModel("나이키", description);

            // assert
            assertThat(brand.getDescription()).isEmpty();
        }

        @DisplayName("이름이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(null, "설명")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel("   ", "설명")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 50자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameExceedsMaxLength() {
            // arrange
            String tooLong = "가".repeat(51);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(tooLong, "설명")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel("나이키", null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 500자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionExceedsMaxLength() {
            // arrange
            String tooLong = "가".repeat(501);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel("나이키", tooLong)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 설명을 변경할 때, ")
    @Nested
    class ChangeDescription {
        @DisplayName("유효한 설명으로 변경하면, 새 설명이 반영된다.")
        @Test
        void updatesDescription_whenValid() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "기존 설명");

            // act
            brand.changeDescription("새 설명");

            // assert
            assertThat(brand.getDescription()).isEqualTo("새 설명");
        }

        @DisplayName("null 로 변경하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenChangingToNull() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "기존 설명");

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.changeDescription(null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
