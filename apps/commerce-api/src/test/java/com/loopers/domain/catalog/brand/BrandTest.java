package com.loopers.domain.catalog.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("이름과 설명이 모두 주어지면 정상 생성된다.")
        @Test
        void createsBrand_whenNameAndDescriptionAreProvided() {
            // arrange
            String name = "Loopers";
            String description = "테스트 브랜드";

            // act
            Brand brand = new Brand(name, description);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description),
                () -> assertThat(brand.isActive()).isTrue()
            );
        }

        @DisplayName("브랜드명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Brand(" ", "테스트 브랜드");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("물리 삭제하지 않고 삭제 상태로 전환한다.")
        @Test
        void marksBrandAsDeleted_whenDeleteIsCalled() {
            // arrange
            Brand brand = new Brand("Loopers", "테스트 브랜드");

            // act
            brand.delete();

            // assert
            assertAll(
                () -> assertThat(brand.getDeletedAt()).isNotNull(),
                () -> assertThat(brand.isActive()).isFalse()
            );
        }
    }
}
