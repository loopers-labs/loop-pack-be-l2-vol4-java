package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandModelTest {

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 이름으로 생성하면, BrandModel이 정상 생성된다.")
        @Test
        void createsBrand_whenNameIsValid() {
            // arrange
            String name = "나이키";

            // act
            BrandModel brand = new BrandModel(name);

            // assert
            assertThat(brand.getName()).isEqualTo(name);
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act & assert
            assertThatThrownBy(() -> new BrandModel(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act & assert
            assertThatThrownBy(() -> new BrandModel("   "))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 이름을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 이름으로 수정하면, 이름이 변경된다.")
        @Test
        void updatesName_whenNameIsValid() {
            // arrange
            BrandModel brand = new BrandModel("나이키");

            // act
            brand.update("아디다스");

            // assert
            assertThat(brand.getName()).isEqualTo("아디다스");
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // arrange
            BrandModel brand = new BrandModel("나이키");

            // act & assert
            assertThatThrownBy(() -> brand.update(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            BrandModel brand = new BrandModel("나이키");

            // act & assert
            assertThatThrownBy(() -> brand.update("   "))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
