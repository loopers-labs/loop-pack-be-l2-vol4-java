package com.loopers.domain.brand;

import com.loopers.domain.brand.enums.BrandStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandModelTest {

    private static final String VALID_NAME = "나이키";

    @DisplayName("브랜드 모델 생성 시,")
    @Nested
    class Create {

        static Stream<String> invalidNames() {
            return Stream.of("", " ", "나", "a".repeat(101));
        }

        static Stream<String> validNames() {
            return Stream.of("나이", VALID_NAME, "a".repeat(100));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("invalidNames")
        @DisplayName("이름이 공백이거나 2자 미만 또는 100자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenNameIsOutOfRange(String invalidName) {
            CoreException result = assertThrows(CoreException.class,
                    () -> new BrandModel(invalidName));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("validNames")
        @DisplayName("이름이 2자 이상 100자 이하이면, ACTIVE 상태로 생성된다.")
        void createsBrand_whenNameIsValid(String validName) {
            BrandModel result = new BrandModel(validName);

            assertThat(result.getName()).isEqualTo(validName);
            assertThat(result.getStatus()).isEqualTo(BrandStatus.ACTIVE);
        }
    }

    @DisplayName("브랜드 삭제 시,")
    @Nested
    class Delete {

        @DisplayName("삭제하면, 상태가 INACTIVE 로 변경된다.")
        @Test
        void deactivatesBrand_whenDeleted() {
            BrandModel brand = new BrandModel(VALID_NAME);

            brand.delete();

            assertThat(brand.getStatus()).isEqualTo(BrandStatus.INACTIVE);
            assertThat(brand.getDeletedAt()).isNotNull();
        }
    }
}
