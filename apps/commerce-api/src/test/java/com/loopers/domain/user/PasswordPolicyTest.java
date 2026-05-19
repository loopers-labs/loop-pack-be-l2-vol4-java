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

        @DisplayName("생년월일 연속 토큰이 포함되지 않은 비밀번호면 통과한다")
        @Test
        void passes_whenPasswordDoesNotContainBirthDate() {
            // given
            RawPassword password = new RawPassword("Aa3!xyz@");

            // when & then
            assertDoesNotThrow(() -> PasswordPolicy.validate(password, VALID_BIRTH_DATE));
        }

        @DisplayName("월(MM)이나 일(DD) 두 자리만 포함된 비밀번호는 통과한다")
        @Test
        void passes_whenPasswordContainsOnlyMonthOrDay() {
            // given - 생년월일 2002-05-11 기준, 월(05)/일(11)/연도뒤2자리(02) 단독 포함은 허용
            RawPassword password = new RawPassword("Aa!05xy11");

            // when & then
            assertDoesNotThrow(() -> PasswordPolicy.validate(password, VALID_BIRTH_DATE));
        }

        @DisplayName("yyyyMMdd 연속 토큰이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsFullBirthDate() {
            // given - 생년월일 2002-05-11 → 20020511
            RawPassword password = new RawPassword("Aa!20020511xy@");

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(password, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("yyMMdd 연속 토큰이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordContainsShortBirthDate() {
            // given - 생년월일 2002-05-11 → 020511
            RawPassword password = new RawPassword("Aa!020511");

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

        @DisplayName("비밀번호가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenPasswordIsNull() {
            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> PasswordPolicy.validate(null, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
