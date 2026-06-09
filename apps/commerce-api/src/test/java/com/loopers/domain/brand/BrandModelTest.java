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
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandModelTest {

    private static final String NAME = "나이키";
    private static final String DESCRIPTION = "스포츠 브랜드";

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면, 브랜드가 생성된다.")
        @Test
        void createsBrand_whenValid() {
            BrandModel brand = new BrandModel(NAME, DESCRIPTION);

            assertThat(brand.getName()).isEqualTo(NAME);
            assertThat(brand.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(brand.getDeletedAt()).isNull();
        }

        @DisplayName("name 이 null/공백이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void throwsBadRequest_whenNameIsBlank(String invalidName) {
            CoreException ex = assertThrows(CoreException.class, () ->
                new BrandModel(invalidName, DESCRIPTION)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("description 이 null/공백이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void throwsBadRequest_whenDescriptionIsBlank(String invalidDescription) {
            CoreException ex = assertThrows(CoreException.class, () ->
                new BrandModel(NAME, invalidDescription)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 값이면, name 과 description 이 갱신된다.")
        @Test
        void updatesBrand_whenValid() {
            BrandModel brand = new BrandModel(NAME, DESCRIPTION);

            brand.update("아디다스", "독일 스포츠 브랜드");

            assertThat(brand.getName()).isEqualTo("아디다스");
            assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드");
        }

        @DisplayName("name 이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewNameIsBlank() {
            BrandModel brand = new BrandModel(NAME, DESCRIPTION);

            CoreException ex = assertThrows(CoreException.class, () ->
                brand.update("", DESCRIPTION)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 소프트 딜리트할 때, ")
    @Nested
    class Delete {

        @DisplayName("delete 를 호출하면, deletedAt 이 기록된다.")
        @Test
        void marksDeletedAt_whenDeleted() {
            BrandModel brand = new BrandModel(NAME, DESCRIPTION);

            brand.delete();

            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("이미 삭제된 브랜드를 다시 삭제해도, 멱등하게 동작한다.")
        @Test
        void isIdempotent_whenDeletedTwice() {
            BrandModel brand = new BrandModel(NAME, DESCRIPTION);

            brand.delete();
            var firstDeletedAt = brand.getDeletedAt();
            brand.delete();

            assertThat(brand.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }
}
