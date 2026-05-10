package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginIdTest {

    @DisplayName("로그인 ID 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("4~20자의 영문/숫자 조합이면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenValueIsAlphanumericAndLengthValid() {
            // arrange
            String value = "loopers123";

            // act
            LoginId loginId = new LoginId(value);

            // assert
            assertAll(
                () -> assertThat(loginId).isNotNull(),
                () -> assertThat(loginId.value()).isEqualTo(value)
            );
        }

        @DisplayName("값이 null 이거나 공백으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void throwsBadRequest_whenValueIsNullOrBlank(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값의 길이가 4~20자 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "abc",
            "abcdefghij1234567890X"
        })
        void throwsBadRequest_whenLengthIsOutOfRange(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문/숫자 외 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "loop한글",
            "loop@123",
            "loop 123",
            "loop-123",
            "loop_123"
        })
        void throwsBadRequest_whenValueContainsNonAlphanumeric(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
