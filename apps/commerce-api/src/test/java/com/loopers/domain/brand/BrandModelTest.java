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

    @DisplayName("BrandModel 의 이름을 변경할 때, ")
    @Nested
    class ChangeName {

        @DisplayName("유효한 새 이름이면, 이름이 그 값으로 변경된다.")
        @Test
        void updatesName_whenNewNameIsValid() {
            // given
            BrandModel brand = new BrandModel("나이키", "Just Do It");

            // when
            brand.changeName("아디다스");

            // then
            assertThat(brand.getName()).isEqualTo("아디다스");
        }

        @DisplayName("새 이름이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewNameIsNull() {
            // given
            BrandModel brand = new BrandModel("나이키", "Just Do It");

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brand.changeName(null));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("브랜드명은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("새 이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewNameIsEmpty() {
            // given
            BrandModel brand = new BrandModel("나이키", "Just Do It");

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brand.changeName(""));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 이름이 공백 문자로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewNameIsSpacesOnly() {
            // given
            BrandModel brand = new BrandModel("나이키", "Just Do It");

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brand.changeName("   "));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 삭제된 브랜드이면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsBrandNotFound_whenBrandIsDeleted() {
            // given
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            brand.delete();

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brand.changeName("아디다스"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }
    }

    @DisplayName("BrandModel 의 설명을 변경할 때, ")
    @Nested
    class ChangeDescription {

        @DisplayName("새 설명을 전달하면, 설명이 그 값으로 변경된다.")
        @Test
        void updatesDescription_whenNewDescriptionIsGiven() {
            // given
            BrandModel brand = new BrandModel("나이키", "Just Do It");

            // when
            brand.changeDescription("새 슬로건");

            // then
            assertThat(brand.getDescription()).isEqualTo("새 슬로건");
        }

        @DisplayName("이미 삭제된 브랜드이면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsBrandNotFound_whenBrandIsDeleted() {
            // given
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            brand.delete();

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brand.changeDescription("새 슬로건"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }
    }

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
