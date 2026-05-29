package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandTest {

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 이름이 주어지면, 브랜드가 생성된다.")
        @Test
        void createsBrand_whenNameIsValid() {
            Brand brand = new Brand("나이키");
            assertThat(brand.getName()).isEqualTo("나이키");
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> new Brand(null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException result = assertThrows(CoreException.class, () -> new Brand("   "));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsEmpty() {
            CoreException result = assertThrows(CoreException.class, () -> new Brand(""));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드명을 수정할 때,")
    @Nested
    class UpdateName {

        @DisplayName("유효한 새 이름이 주어지면, 이름이 변경된다.")
        @Test
        void updatesName_whenNewNameIsValid() {
            Brand brand = new Brand("나이키");
            brand.updateName("아디다스");
            assertThat(brand.getName()).isEqualTo("아디다스");
        }

        @DisplayName("새 이름이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewNameIsBlank() {
            Brand brand = new Brand("나이키");
            CoreException result = assertThrows(CoreException.class, () -> brand.updateName("   "));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewNameIsNull() {
            Brand brand = new Brand("나이키");
            CoreException result = assertThrows(CoreException.class, () -> brand.updateName(null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드를 삭제 처리할 때,")
    @Nested
    class Delete {

        @DisplayName("삭제 후 deletedAt이 설정된다.")
        @Test
        void setsDeletedAt_whenDeleted() {
            Brand brand = new Brand("나이키");
            brand.delete();
            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("삭제는 멱등하게 동작한다.")
        @Test
        void deleteIsIdempotent() {
            Brand brand = new Brand("나이키");
            brand.delete();
            var firstDeletedAt = brand.getDeletedAt();
            brand.delete();
            assertThat(brand.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }
}
