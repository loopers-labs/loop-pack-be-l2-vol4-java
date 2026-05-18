package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PasswordTest {

    private static final LocalDate DEFAULT_BIRTHDAY = LocalDate.of(1992, 6, 24);

    @Nested
    @DisplayName("비밀번호 검증")
    class PasswordValidation {

        @DisplayName("비밀번호가 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        void given_nullOrBlankPassword_when_createPassword_then_throwsBadRequestException(String invalidPassword) {
            // Act
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new Password(invalidPassword, DEFAULT_BIRTHDAY)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8자 미만이거나 16자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {
                "Abc1!",                  // 5자 (미만)
                "Abc123!",                // 7자 (미만, 경계 직전)
                "Abc12345!Abcdefgh",       // 17자 (초과, 경계 직후)
                "Abc12345!Abcdefgh1234"   // 21자 (초과)
        })
        void given_passwordLengthOutOfRange_when_createPassword_then_throwsBadRequestException(String invalidPassword) {
            // Act
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new Password(invalidPassword, DEFAULT_BIRTHDAY)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않은 문자(한글 등)가 포함되면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {
                "Password한글1!",          // 한글
                "Pass word1!",             // 공백
                "Password1!\t",            // 탭
                "Pässword1!"               // 특수 유니코드
        })
        void given_passwordWithInvalidCharacters_when_createPassword_then_throwsBadRequestException(String invalidPassword) {
            // Act
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new Password(invalidPassword, DEFAULT_BIRTHDAY)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(YYYYMMDD)이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_passwordContainingBirthdayYYYYMMDD_when_createPassword_then_throwsBadRequestException() {
            String password = "Pass19920624!";

            // Act
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new Password(password, DEFAULT_BIRTHDAY)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(YYMMDD)이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_passwordContainingBirthdayYYMMDD_when_createPassword_then_throwsBadRequestException() {
            // Arrange
            String password = "Pass920624!";

            // Act
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new Password(password, DEFAULT_BIRTHDAY)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(MMDD)이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_passwordContainingBirthdayMMDD_when_createPassword_then_throwsBadRequestException() {
            // Arrange
            String password = "Password0624!";

            // Act
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new Password(password, DEFAULT_BIRTHDAY)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        //해피케이스
        @DisplayName("비밀번호가 허용 문자로만 구성되고 8~16자이며 생년월일을 포함하지 않으면 정상 생성된다")
        @ParameterizedTest
        @ValueSource(strings = {
                "abcdefgh",                      // 8자, 영소문자만 (최소 경계)
                "ABCDEFGH",                      // 8자, 영대문자만
                "12345678",                      // 8자, 숫자만
                "!@#$%^&*",                      // 8자, 특수문자만
                "Password",                      // 영문 혼합
                "Password9!",                    // 다종 조합
                "Abcdefgh12345!@#"               // 16자 (최대 경계)
        })
        void given_validPassword_when_createPassword_then_createsPassword(String validPassword) {
            // Arrange

            // Act
            Password password = new Password(validPassword, DEFAULT_BIRTHDAY);


            // Assert  예외 없이 생성되면 성공
            assertThat(password).isNotNull();
        }
    }
}
