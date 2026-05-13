package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordTest {

    @DisplayName("비밀번호 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 형식이면 생성 성공")
        @Test
        void createsPassword_whenFormatIsValid() {
            // arrange
            String validValue = "chan1234!";

            // act
            Password password = new Password(validValue);

            // assert
            assertThat(password.value()).isEqualTo(validValue);
        }

        @DisplayName("null이면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Password(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백이면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenValueIsBlank() {
            // arrange
            String blank = "        ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> new Password(blank));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("8자 미만이면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenValueIsShorterThanMin() {
            // arrange
            String shortValue = "Ab12!";

            // act
            CoreException result = assertThrows(CoreException.class, () -> new Password(shortValue));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("16자 초과이면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenValueIsLongerThanMax() {
            // arrange
            String longValue = "Password12345678!";

            // act
            CoreException result = assertThrows(CoreException.class, () -> new Password(longValue));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문 대소문자/숫자/특수문자 외 문자포함 시 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenValueContainsDisallowedCharacter() {
            // arrange
            String invalidValue = "Pass1234가!";

            // act
            CoreException result = assertThrows(CoreException.class, () -> new Password(invalidValue));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
