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

    @DisplayName("비밀번호 정책 검증 시")
    @Nested
    class Validate {

        @DisplayName("생년월일 조각이 포함되지 않은 비밀번호면 통과한다")
        @Test
        void passes_whenPasswordDoesNotContainBirthDate() {
            // given
            RawPassword password = new RawPassword("Aa3!xyz@");

            // when & then
            assertDoesNotThrow(() -> PasswordPolicy.validate(password, VALID_BIRTH_DATE));
        }

        @DisplayName("생년월일 전체 연도(YYYY)가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsFullYear() {
            // given
            RawPassword password = new RawPassword("Aa!2002xyz");

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(password, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 뒤 2자리(YY) 연도가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsShortYear() {
            // given
            RawPassword password = new RawPassword("Aabc!@xy02");

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(password, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 월(MM)이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsMonth() {
            // given
            RawPassword password = new RawPassword("Aabc!@xy05");

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(password, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 일(DD)이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsDay() {
            // given
            RawPassword password = new RawPassword("Aabc!@xy11");

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(password, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // given
            RawPassword password = new RawPassword("Aa3!xyz@");

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(password, null));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
