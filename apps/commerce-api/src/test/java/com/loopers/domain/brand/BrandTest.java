package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 이름이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBrand_whenNameIsValid() {
            // Arrange & Act
            Brand brand = Brand.create("나이키");

            // Assert
            assertThat(brand.getName()).isEqualTo("나이키");
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // Arrange & Act
            CoreException result = assertThrows(CoreException.class, () ->
                Brand.create(null)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // Arrange & Act
            CoreException result = assertThrows(CoreException.class, () ->
                Brand.create("   ")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 이름을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("올바른 이름이 주어지면, 이름이 변경된다.")
        @Test
        void updatesName_whenNameIsValid() {
            // Arrange
            Brand brand = Brand.create("나이키");

            // Act
            brand.update("아디다스");

            // Assert
            assertThat(brand.getName()).isEqualTo("아디다스");
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // Arrange
            Brand brand = Brand.create("나이키");

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update(null)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // Arrange
            Brand brand = Brand.create("나이키");

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                brand.update("   ")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 소프트딜리트할 때, ")
    @Nested
    class SoftDelete {

        @DisplayName("delete() 호출 후 deletedAt이 설정된다.")
        @Test
        void setsDeletedAt_whenDeleted() {
            // Arrange
            Brand brand = Brand.create("나이키");

            // Act
            brand.delete();

            // Assert
            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("delete() 호출 전에는 deletedAt이 null이다.")
        @Test
        void deletedAtIsNull_beforeDeleted() {
            // Arrange & Act
            Brand brand = Brand.create("나이키");

            // Assert
            assertThat(brand.getDeletedAt()).isNull();
        }
    }
}
