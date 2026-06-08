package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BrandModelTest {
    private static final String VALID_NAME = "Nike";
    private static final String VALID_DESCRIPTION = "스포츠 브랜드";

    @DisplayName("정상 입력값으로 BrandModel 을 생성할 수 있다.")
    @Test
    void createsBrandModel_withValidNameAndDescription() {
        // arrange & act
        BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

        // assert
        assertThat(brand.getName()).isEqualTo(VALID_NAME);
        assertThat(brand.getDescription()).isEqualTo(VALID_DESCRIPTION);
    }

    @DisplayName("브랜드명이 비어있다면, BAD_REQUEST 예외가 발생한다.")
    @Test
    void createBadRequest_whenNameIsBlank() {
        // arrange
        String blankName = "  ";

        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(blankName, VALID_DESCRIPTION));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("브랜드명이 50자를 넘어가면, BAD_REQUEST 예외가 발생한다.")
    @Test
    void createBadRequest_whenNameExceedsMaxLength() {
        // arrange
        String tooLongName = "a".repeat(51);

        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(tooLongName, VALID_DESCRIPTION));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("브랜드 설명이 500자를 넘어가면, BAD_REQUEST 예외가 발생한다.")
    @Test
    void createBadRequest_whenDescriptionExceedsMaxLength() {
        // arrange
        String tooLongDescription = "a".repeat(501);

        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(VALID_NAME, tooLongDescription));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("브랜드 설명이 null 이어도 생성할 수 있다.")
    @Test
    void createsBrandModel_whenDescriptionIsNull() {
        // arrange & act
        BrandModel brand = new BrandModel(VALID_NAME, null);

        // assert
        assertThat(brand.getName()).isEqualTo(VALID_NAME);
        assertThat(brand.getDescription()).isNull();
    }
}
