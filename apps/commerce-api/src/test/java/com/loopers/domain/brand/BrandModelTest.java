package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandModelTest {

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("올바른 정보로 생성하면 정상적으로 생성된다.")
        @Test
        void createsBrand_whenAllFieldsAreValid() {
            // arrange
            String name = "나이키";
            String description = "스포츠 브랜드";

            // act
            BrandModel brand = new BrandModel(name, description);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description),
                () -> assertThat(brand.isDeleted()).isFalse()
            );
        }

        @DisplayName("이름이 null 또는 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        void throwsBadRequest_whenNameIsBlank(String name) {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new BrandModel(name, "설명"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 null 또는 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        void throwsBadRequest_whenDescriptionIsBlank(String description) {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new BrandModel("나이키", description));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 정보를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("이름과 설명이 변경된다.")
        @Test
        void updatesNameAndDescription() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");

            // act
            brand.update("아디다스", "독일 스포츠 브랜드");

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("아디다스"),
                () -> assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드")
            );
        }

        @DisplayName("이름이 공백이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewNameIsBlank() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> brand.update("", "독일 스포츠 브랜드"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("isDeleted 가 true 가 된다.")
        @Test
        void marksAsDeleted() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");

            // act
            brand.delete();

            // assert
            assertThat(brand.isDeleted()).isTrue();
        }

        @DisplayName("이미 삭제된 브랜드를 다시 삭제해도 멱등하게 동작한다.")
        @Test
        void isIdempotent() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드");
            brand.delete();

            // act & assert
            brand.delete();
            assertThat(brand.isDeleted()).isTrue();
        }
    }
}
