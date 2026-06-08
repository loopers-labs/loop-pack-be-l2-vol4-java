package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandModelTest {

    private static final String VALID_NAME = "Nike";
    private static final String VALID_DESCRIPTION = "스포츠 브랜드";

    @DisplayName("BrandModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 이름과 설명으로 생성 시 필드가 정상 설정된다.")
        @Test
        void createsBrandModel_whenAllFieldsAreValid() {
            // arrange & act
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(brand.getDescription()).isEqualTo(VALID_DESCRIPTION),
                () -> assertThat(brand.isDeleted()).isFalse()
            );
        }

        @DisplayName("설명 없이(null description) 생성 시 정상 생성된다.")
        @Test
        void createsBrandModel_whenDescriptionIsNull() {
            // arrange & act
            BrandModel brand = new BrandModel(VALID_NAME, null);

            // assert
            assertThat(brand.getName()).isEqualTo(VALID_NAME);
            assertThat(brand.getDescription()).isNull();
        }

        @DisplayName("null 브랜드명으로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel(null, VALID_DESCRIPTION)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백 브랜드명으로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                new BrandModel("   ", VALID_DESCRIPTION)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("update()를 호출할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 이름과 설명으로 수정 시 필드가 갱신된다.")
        @Test
        void updatesFields_whenValidValuesProvided() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            brand.update("Adidas", "독일 스포츠 브랜드");

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("Adidas"),
                () -> assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드")
            );
        }

        @DisplayName("null 브랜드명으로 수정 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update(null, "설명")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백 브랜드명으로 수정 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update("  ", "설명")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("validateActive()를 호출할 때,")
    @Nested
    class ValidateActive {

        @DisplayName("활성 브랜드는 예외 없이 통과한다.")
        @Test
        void doesNotThrow_whenBrandIsActive() {
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);
            assertDoesNotThrow(brand::validateActive);
        }

        @DisplayName("삭제된 브랜드는 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIsDeleted() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);
            brand.delete();

            // act
            CoreException result = assertThrows(CoreException.class, brand::validateActive);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("isDeleted()를 호출할 때,")
    @Nested
    class IsDeleted {

        @DisplayName("삭제되지 않은 브랜드는 false를 반환한다.")
        @Test
        void returnsFalse_whenBrandIsNotDeleted() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act & assert
            assertThat(brand.isDeleted()).isFalse();
        }

        @DisplayName("delete() 호출 후 true를 반환한다.")
        @Test
        void returnsTrue_afterDeleteCalled() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);

            // act
            brand.delete();

            // assert
            assertThat(brand.isDeleted()).isTrue();
        }

        @DisplayName("delete()는 멱등하게 동작한다.")
        @Test
        void isIdempotent_whenDeleteCalledMultipleTimes() {
            // arrange
            BrandModel brand = new BrandModel(VALID_NAME, VALID_DESCRIPTION);
            brand.delete();

            // act
            brand.delete(); // 두 번 호출해도 예외 없음

            // assert
            assertThat(brand.isDeleted()).isTrue();
        }
    }
}
