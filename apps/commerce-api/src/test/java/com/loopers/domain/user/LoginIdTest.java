package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginIdTest {

    @DisplayName("LoginId 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 문자열 또는 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void throwsBadRequest_whenValueIsBlank(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문/숫자 외 문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"kim_99", "kim-99", "김민수", "kim 99", "kim.99", "kim@99"})
        void throwsBadRequest_whenValueContainsInvalidCharacters(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문/숫자 조합이면 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"kim", "12345", "kim99", "KIM99", "abcXYZ123"})
        void createsLoginId_whenValueContainsOnlyAlphanumeric(String value) {
            // act
            LoginId loginId = new LoginId(value);

            // assert
            assertThat(loginId.getValue()).isEqualTo(value);
        }
    }
}
