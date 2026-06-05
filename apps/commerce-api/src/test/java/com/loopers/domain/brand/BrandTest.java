package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("이름과 설명이 모두 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenNameAndDescriptionAreProvided() {
            String name = "APPLE";
            String description = "전자기기 판매";

            Brand brand = new Brand(name, description);

            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Brand("   ", "설명"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 20자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameExceeds20Characters() {
            String longName = "a".repeat(21);
            CoreException ex = assertThrows(CoreException.class,
                    () -> new Brand(longName, "설명"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 20자이면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenNameIsExactly20Characters() {
            String name = "a".repeat(20);
            Brand brand = new Brand(name, "설명");
            assertThat(brand.getName()).isEqualTo(name);
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Brand(null, "설명"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsBlank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Brand("브랜드", ""));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("설명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDescriptionIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new Brand("브랜드", null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 정보를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 이름과 설명으로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenValidValuesAreProvided() {
            Brand brand = new Brand("원래 브랜드", "원래 설명");

            brand.update("새 브랜드", "새 설명");

            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("새 브랜드"),
                () -> assertThat(brand.getDescription()).isEqualTo("새 설명")
            );
        }

        @DisplayName("수정할 이름이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUpdateNameIsBlank() {
            Brand brand = new Brand("브랜드", "설명");
            CoreException ex = assertThrows(CoreException.class,
                () -> brand.update("  ", "새 설명"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수정할 설명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUpdateDescriptionIsNull() {
            Brand brand = new Brand("브랜드", "설명");
            CoreException ex = assertThrows(CoreException.class,
                () -> brand.update("새 브랜드", null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
