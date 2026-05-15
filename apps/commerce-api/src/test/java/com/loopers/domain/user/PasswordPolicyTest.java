package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @DisplayName("비밀번호 정책을 검증할 때, ")
    @Nested
    class Validate {
        @DisplayName("길이가 8~16자이고 허용 문자만 포함하면, 정상적으로 통과한다.")
        @Test
        void validatesPassword_whenPasswordMatchesPolicy() {
            // arrange
            String password = "abc123!?";
            LocalDate birth = LocalDate.of(1990, 1, 15);

            // act & assert
            assertDoesNotThrow(() -> passwordPolicy.validate(password, birth));
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsTooShort() {
            // arrange
            String password = "abc12!?";
            LocalDate birth = LocalDate.of(1990, 1, 15);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                passwordPolicy.validate(password, birth);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsTooLong() {
            // arrange
            String password = "abcd1234!?abcd1234";
            LocalDate birth = LocalDate.of(1990, 1, 15);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                passwordPolicy.validate(password, birth);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("S1 특수문자는 모두 허용된다.")
        @Test
        void validatesPassword_whenPasswordContainsAllowedSpecialCharacters() {
            // arrange
            String password = "!@#$%^&*()-_+=?";
            LocalDate birth = LocalDate.of(1990, 1, 15);

            // act & assert
            assertDoesNotThrow(() -> passwordPolicy.validate(password, birth));
        }

        @DisplayName("허용되지 않은 특수문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsDisallowedCharacter() {
            // arrange
            String password = "abc1234~";
            LocalDate birth = LocalDate.of(1990, 1, 15);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                passwordPolicy.validate(password, birth);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 yyyyMMdd 패턴이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDateAsYyyyMMdd() {
            // arrange
            String password = "pw19900115!";
            LocalDate birth = LocalDate.of(1990, 1, 15);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                passwordPolicy.validate(password, birth);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 yyMMdd 패턴이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDateAsYyMMdd() {
            // arrange
            String password = "pw900115!";
            LocalDate birth = LocalDate.of(1990, 1, 15);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                passwordPolicy.validate(password, birth);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 MMdd 패턴만 포함되면, 정상적으로 통과한다.")
        @Test
        void validatesPassword_whenPasswordContainsBirthDateAsMmddOnly() {
            // arrange
            String password = "pw0115!?";
            LocalDate birth = LocalDate.of(1990, 1, 15);

            // act & assert
            assertDoesNotThrow(() -> passwordPolicy.validate(password, birth));
        }
    }
}
