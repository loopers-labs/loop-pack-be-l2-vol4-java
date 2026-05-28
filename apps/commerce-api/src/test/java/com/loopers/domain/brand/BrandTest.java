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

    @DisplayName("브랜드 모델을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsBrandModel_whenAllFieldsAreValid() {
            // arrange
            String name = "Loopers";
            String description = "감성 이커머스 브랜드";

            // act
            Brand brand = new Brand(name, description);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description),
                () -> assertThat(brand.isVisible()).isTrue()
            );
        }

        @DisplayName("브랜드명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String name = " ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Brand(name, "감성 이커머스 브랜드");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 정보를 수정할 때, ")
    @Nested
    class Update {
        @DisplayName("유효한 값이 주어지면, 브랜드 정보를 변경한다.")
        @Test
        void updatesBrandInfo_whenFieldsAreValid() {
            // arrange
            Brand brand = new Brand("Loopers", "감성 이커머스 브랜드");

            // act
            brand.update("New Loopers", "새로운 브랜드 설명");

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("New Loopers"),
                () -> assertThat(brand.getDescription()).isEqualTo("새로운 브랜드 설명")
            );
        }

        @DisplayName("브랜드 설명이 비어있으면, BAD_REQUEST 예외가 발생하고 기존 값은 유지된다.")
        @Test
        void throwsBadRequestException_whenDescriptionIsBlank() {
            // arrange
            Brand brand = new Brand("Loopers", "감성 이커머스 브랜드");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brand.update("New Loopers", " ");
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(brand.getName()).isEqualTo("Loopers"),
                () -> assertThat(brand.getDescription()).isEqualTo("감성 이커머스 브랜드")
            );
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {
        @DisplayName("삭제된 브랜드는 사용자에게 노출되지 않는다.")
        @Test
        void returnsInvisible_whenBrandIsDeleted() {
            // arrange
            Brand brand = new Brand("Loopers", "감성 이커머스 브랜드");

            // act
            brand.delete();

            // assert
            assertThat(brand.isVisible()).isFalse();
        }
    }
}
