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
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordTest {

    private static final PasswordHasher FAKE_HASHER = new PasswordHasher() {
        @Override public String encode(String raw) { return raw; }
        @Override public boolean matches(String raw, String encoded) { return raw.equals(encoded); }
    };

    @DisplayName("Password 를 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("8~16자 영문/숫자/특수문자이면 생성에 성공하고 저장된다")
        @ValueSource(strings = {"Password1!", "Abc12345!", "aA1!aA1!", "abcdefghij12345!"})
        @ParameterizedTest
        void createsPassword_whenValueIsValid(String value) {
            // act
            Password password = Password.of(value, FAKE_HASHER);

            // assert
            assertThat(password.matches(value, FAKE_HASHER)).isTrue();
        }

        @DisplayName("null 이거나 빈 문자열이면 BAD_REQUEST 예외가 발생한다")
        @NullAndEmptySource
        @ParameterizedTest
        void throwsBadRequestException_whenValueIsNullOrEmpty(String value) {
            CoreException result = assertThrows(CoreException.class, () -> Password.of(value, FAKE_HASHER));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("7자 이하이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenValueIsTooShort() {
            CoreException result = assertThrows(CoreException.class, () -> Password.of("Pw1!", FAKE_HASHER));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("17자 이상이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenValueIsTooLong() {
            CoreException result = assertThrows(CoreException.class, () -> Password.of("Password1!Password1!", FAKE_HASHER));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenValueContainsKorean() {
            CoreException result = assertThrows(CoreException.class, () -> Password.of("한글Password1!", FAKE_HASHER));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenValueContainsSpace() {
            CoreException result = assertThrows(CoreException.class, () -> Password.of("Password 1!", FAKE_HASHER));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
