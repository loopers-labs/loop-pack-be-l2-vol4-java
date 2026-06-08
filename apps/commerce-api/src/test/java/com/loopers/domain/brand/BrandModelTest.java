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

        @DisplayName("이름이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenNameIsProvided() {
            // arrange
            String name = "나이키";
            String description = "스포츠 브랜드";
            String logoUrl = "https://example.com/nike.png";

            // act
            BrandModel brand = new BrandModel(name, description, logoUrl);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description),
                () -> assertThat(brand.getLogoUrl()).isEqualTo(logoUrl)
            );
        }

        @DisplayName("설명과 로고가 없어도, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenOptionalFieldsAreNull() {
            // act
            BrandModel brand = new BrandModel("나이키", null, null);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo("나이키"),
                () -> assertThat(brand.getDescription()).isNull(),
                () -> assertThat(brand.getLogoUrl()).isNull()
            );
        }

        @DisplayName("이름이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandModel("   ", null, null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new BrandModel(null, null, null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
