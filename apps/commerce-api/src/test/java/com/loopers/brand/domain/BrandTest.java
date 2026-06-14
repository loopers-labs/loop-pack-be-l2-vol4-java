package com.loopers.brand.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class BrandTest {

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 이름과 설명이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenNameAndDescriptionAreValid() {
            // arrange
            String name = "애플";
            String description = "기술과 디자인으로 일상을 새롭게 만드는 브랜드";

            // act
            Brand brand = Brand.create(name, description);

            // assert
            assertAll(
                () -> assertThat(brand.getName().value()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description),
                () -> assertThat(brand.getDeletedAt()).isNull()
            );
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String blankName = "  ";

            // act & assert
            assertThatThrownBy(() -> Brand.create(blankName, "설명"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 이름과 설명이 주어지면, 브랜드 정보가 변경된다.")
        @Test
        void updatesBrand_whenNameAndDescriptionAreValid() {
            // arrange
            Brand brand = Brand.create("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");

            // act
            brand.update("애플 스토어", "사용자 경험과 서비스를 함께 제공하는 브랜드");

            // assert
            assertAll(
                () -> assertThat(brand.getName().value()).isEqualTo("애플 스토어"),
                () -> assertThat(brand.getDescription()).isEqualTo("사용자 경험과 서비스를 함께 제공하는 브랜드")
            );
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewNameIsBlank() {
            // arrange
            Brand brand = Brand.create("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");

            // act & assert
            assertThatThrownBy(() -> brand.update(" ", "설명"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("삭제 시각이 기록되고, 다시 삭제해도 같은 삭제 시각을 유지한다.")
        @Test
        void keepsDeletedAt_whenDeleteIsCalledTwice() {
            // arrange
            Brand brand = Brand.create("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");

            // act
            brand.delete();
            var firstDeletedAt = brand.getDeletedAt();
            brand.delete();

            // assert
            assertAll(
                () -> assertThat(firstDeletedAt).isNotNull(),
                () -> assertThat(brand.getDeletedAt()).isEqualTo(firstDeletedAt)
            );
        }
    }
}
