package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private static final BirthDate BIRTH_DATE = new BirthDate(LocalDate.of(1999, 1, 1));

    @DisplayName("PasswordPolicy 검증 시, ")
    @Nested
    class Validate {

        @DisplayName("비밀번호가 null/빈 값이거나 8~16자 범위를 벗어나면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "Aa1!aa1", "Aa1!Aa1!Aa1!Aa1!A"})
        void throwsBadRequest_whenLengthIsOutOfRange(String rawPassword) {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(rawPassword, BIRTH_DATE));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("허용 외 문자(한글, 공백, 탭)가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Abcd123!한글", "Abcd 123!", "Abcd\t123!", "        "})
        void throwsBadRequest_whenContainsInvalidCharacters(String rawPassword) {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(rawPassword, BIRTH_DATE));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일 substring (yyMMdd / yyyyMMdd / 구분자 변형) 이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "Aa1!990101",
            "Aa1!19990101",
            "Aa1!99-01-01",
            "Aa1!1999-01-01",
            "Aa1!99/01/01",
            "Aa1!1999/01/01",
            "Aa1!99.01.01",
            "Aa1!1999.01.01"
        })
        void throwsBadRequest_whenContainsBirthDate(String rawPassword) {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(rawPassword, BIRTH_DATE));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문 대/소문자 + 숫자 + 특수문자 조합 8~16자이고 생년월일 substring 미포함이면 예외 없이 통과한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Abcd123!", "Z!aB9@xY7#kL", "P@ssw0rdABCD1234"})
        void passes_whenValid(String rawPassword) {
            // act & assert
            assertThatCode(() -> PasswordPolicy.validate(rawPassword, BIRTH_DATE))
                .doesNotThrowAnyException();
        }
    }
}
