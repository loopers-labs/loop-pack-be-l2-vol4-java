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

    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2002, 5, 11);

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @DisplayName("평문 비밀번호 규칙 검증 시")
    @Nested
    class Validate {

        @DisplayName("8~16자 ASCII 인쇄 가능 문자로 구성되고 생년월일이 포함되지 않으면 통과한다")
        @Test
        void passes_whenPasswordIsValid() {
            // given
            String password = "Aa3!xyz@";

            // when / then
            assertDoesNotThrow(() -> passwordPolicy.validate(password, VALID_BIRTH_DATE));
        }

        @DisplayName("비밀번호가 7자면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // given
            String password = "Aa3!xyz";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, VALID_BIRTH_DATE)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 17자면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // given
            String password = "Aa3!xyz@abcdefghi";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, VALID_BIRTH_DATE)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 한글이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsKorean() {
            // given
            String password = "Aa3!한글abc";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, VALID_BIRTH_DATE)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일 전체 연도(YYYY)가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsFullYear() {
            // given
            String password = "Aa!2002xyz";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, VALID_BIRTH_DATE)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일 뒤 2자리(YY) 연도가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsShortYear() {
            // given
            String password = "Aabc!@xy02";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, VALID_BIRTH_DATE)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일 월(MM)이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsMonth() {
            // given
            String password = "Aabc!@xy05";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, VALID_BIRTH_DATE)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일 일(DD)이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsDay() {
            // given
            String password = "Aabc!@xy11";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                passwordPolicy.validate(password, VALID_BIRTH_DATE)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
