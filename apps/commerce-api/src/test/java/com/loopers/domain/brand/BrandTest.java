package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    private static final String NAME = "나이키";
    private static final String DESCRIPTION = "스포츠 브랜드";

    @DisplayName("Brand 를 create 로 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 값이면 id 는 미할당(0), deleted 는 false 인 신규 Brand 가 생성된다.")
        @Test
        void createsNewBrand_whenValid() {
            // act
            Brand brand = Brand.create(NAME, DESCRIPTION);

            // assert
            assertThat(brand.getId()).isEqualTo(0L);
            assertThat(brand.getName()).isEqualTo(NAME);
            assertThat(brand.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(brand.isDeleted()).isFalse();
        }

        @DisplayName("description 이 null 이어도 생성된다.")
        @Test
        void createsBrand_whenDescriptionIsNull() {
            // act
            Brand brand = Brand.create(NAME, null);

            // assert
            assertThat(brand.getDescription()).isNull();
        }

        @DisplayName("name 이 null 이거나 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void throwsBadRequest_whenNameIsBlank(String invalidName) {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> Brand.create(invalidName, DESCRIPTION));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Brand 를 modify 로 수정할 때, ")
    @Nested
    class Modify {

        @DisplayName("정상 값이면 name 과 description 이 모두 변경된다.")
        @Test
        void modifies_whenValid() {
            // arrange
            Brand brand = Brand.create(NAME, DESCRIPTION);

            // act
            brand.modify("아디다스", "독일 스포츠 브랜드");

            // assert
            assertThat(brand.getName()).isEqualTo("아디다스");
            assertThat(brand.getDescription()).isEqualTo("독일 스포츠 브랜드");
        }

        @DisplayName("description 을 null 로도 변경할 수 있다.")
        @Test
        void modifies_whenDescriptionIsNull() {
            // arrange
            Brand brand = Brand.create(NAME, DESCRIPTION);

            // act
            brand.modify("아디다스", null);

            // assert
            assertThat(brand.getDescription()).isNull();
        }

        @DisplayName("name 이 null 이거나 공백이면 BAD_REQUEST 예외가 발생하고 기존 값이 유지된다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "\t"})
        void throwsBadRequest_whenNameIsBlank(String invalidName) {
            // arrange
            Brand brand = Brand.create(NAME, DESCRIPTION);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> brand.modify(invalidName, "다른 설명"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(brand.getName()).isEqualTo(NAME);
            assertThat(brand.getDescription()).isEqualTo(DESCRIPTION);
        }
    }

    @DisplayName("Brand 를 delete 로 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("deleted 상태가 true 로 변경된다.")
        @Test
        void marksAsDeleted() {
            // arrange
            Brand brand = Brand.create(NAME, DESCRIPTION);

            // act
            brand.delete();

            // assert
            assertThat(brand.isDeleted()).isTrue();
        }

        @DisplayName("delete 를 두 번 호출해도 deleted 는 true 그대로 유지된다 (멱등).")
        @Test
        void isIdempotent_whenCalledTwice() {
            // arrange
            Brand brand = Brand.create(NAME, DESCRIPTION);
            brand.delete();

            // act
            brand.delete();

            // assert
            assertThat(brand.isDeleted()).isTrue();
        }
    }
}
