package com.loopers.domain.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class BrandModelTest {

    @DisplayName("BrandModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("이름과 설명이 정책을 통과하면 각 값을 보존한 BrandModel이 생성된다.")
        @Test
        void createsBrandModel_whenNameAndDescriptionAreValid() {
            // arrange
            String rawName = "감성 브랜드";
            String rawDescription = "감성을 담은 브랜드";

            // act
            BrandModel brandModel = BrandModel.builder()
                .rawName(rawName)
                .rawDescription(rawDescription)
                .build();

            // assert
            assertAll(
                () -> assertThat(brandModel.getName()).isEqualTo(Name.from(rawName)),
                () -> assertThat(brandModel.getDescription()).isEqualTo(rawDescription)
            );
        }

        @DisplayName("설명이 없어도(null) 이름만으로 BrandModel이 생성된다.")
        @Test
        void createsBrandModel_whenDescriptionIsNull() {
            // arrange
            String rawName = "감성 브랜드";

            // act
            BrandModel brandModel = BrandModel.builder()
                .rawName(rawName)
                .rawDescription(null)
                .build();

            // assert
            assertAll(
                () -> assertThat(brandModel.getName()).isEqualTo(Name.from(rawName)),
                () -> assertThat(brandModel.getDescription()).isNull()
            );
        }

        @DisplayName("이름이 정책을 위반하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameViolatesPolicy() {
            // arrange
            String invalidName = "가".repeat(51);

            // act & assert
            assertThatThrownBy(() -> BrandModel.builder().rawName(invalidName).rawDescription("설명").build())
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("BrandModel을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("새 이름과 설명으로 값이 갱신된다.")
        @Test
        void updatesNameAndDescription() {
            // arrange
            BrandModel brandModel = BrandModel.builder().rawName("기존 브랜드").rawDescription("기존 설명").build();

            // act
            brandModel.update("새 브랜드", "새 설명");

            // assert
            assertAll(
                () -> assertThat(brandModel.getName()).isEqualTo(Name.from("새 브랜드")),
                () -> assertThat(brandModel.getDescription()).isEqualTo("새 설명")
            );
        }

        @DisplayName("새 이름이 정책을 위반하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewNameViolatesPolicy() {
            // arrange
            BrandModel brandModel = BrandModel.builder().rawName("기존 브랜드").rawDescription("기존 설명").build();

            // act & assert
            assertThatThrownBy(() -> brandModel.update("가".repeat(51), "새 설명"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
