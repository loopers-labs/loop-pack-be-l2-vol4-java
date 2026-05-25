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

    @DisplayName("BrandModel 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("이름과 설명이 유효하면, 정상적으로 생성되고 각 필드가 그대로 보관된다.")
        @Test
        void createsBrandModel_whenAllFieldsAreValid() {
            // given
            String name = "나이키";
            String description = "Just Do It";

            // when
            BrandModel brand = new BrandModel(name, description);

            // then
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("설명이 null 이어도, 정상적으로 생성된다.")
        @Test
        void createsBrandModel_whenDescriptionIsNull() {
            // given
            String name = "아디다스";
            String description = null;

            // when
            BrandModel brand = new BrandModel(name, description);

            // then
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isNull()
            );
        }

        @DisplayName("이름이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // given
            String name = null;
            String description = "Just Do It";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new BrandModel(name, description));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsEmpty() {
            // given
            String name = "";
            String description = "Just Do It";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new BrandModel(name, description));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("이름이 공백 문자로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsSpacesOnly() {
            // given
            String name = "   ";
            String description = "Just Do It";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new BrandModel(name, description));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명은 비어있을 수 없습니다.")
            );
        }
    }
}
