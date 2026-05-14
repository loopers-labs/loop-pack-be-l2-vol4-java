package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private PasswordPolicy passwordPolicy;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);

    @BeforeEach
    void setUp() {
        passwordPolicy = new PasswordPolicy();
    }

    @DisplayName("비밀번호 형식을 검증할 때,")
    @Nested
    class ValidateFormat {

        @DisplayName("Given 유효한 비밀번호 / When 검증 / Then 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPasswordIsValid() {
            // arrange
            String password = "ValidPw1!";

            // act & assert
            assertDoesNotThrow(() -> passwordPolicy.validate(password, BIRTH_DATE));
        }

        @DisplayName("Given 8자 비밀번호 (최소 길이) / When 검증 / Then 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPasswordHasMinLength() {
            // arrange
            String password = "Valid1!@";

            // act & assert
            assertDoesNotThrow(() -> passwordPolicy.validate(password, BIRTH_DATE));
        }

        @DisplayName("Given 16자 비밀번호 (최대 길이) / When 검증 / Then 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPasswordHasMaxLength() {
            // arrange
            String password = "ValidPassword1!@";

            // act & assert
            assertDoesNotThrow(() -> passwordPolicy.validate(password, BIRTH_DATE));
        }

        @DisplayName("Given 7자 비밀번호 (최소 길이 미만) / When 검증 / Then BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            String password = "Short1!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("Given 17자 비밀번호 (최대 길이 초과) / When 검증 / Then BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            String password = "TooLongPassword1!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("Given 허용되지 않는 문자(한글) 포함 / When 검증 / Then BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacter() {
            // arrange
            String password = "패스워드1234!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("Given null 비밀번호 / When 검증 / Then BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(null, BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("생년월일 포함 여부를 검증할 때,")
    @Nested
    class ValidateBirthDate {

        @DisplayName("Given yyyyMMdd 형식의 생년월일 포함 / When 검증 / Then BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDateAsYyyyMMdd() {
            // arrange
            String password = "Pass19900115";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("Given yyyy-MM-dd 형식의 생년월일 포함 / When 검증 / Then BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDateAsIso() {
            // arrange
            String password = "1990-01-15P!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("Given MMdd 형식의 생년월일 포함 / When 검증 / Then BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDateAsMMdd() {
            // arrange
            String password = "Pass0115Aa!b";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("Given yyyyMM 형식의 생년월일 포함 / When 검증 / Then BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDateAsYyyyMM() {
            // arrange
            String password = "Pass199001A!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
