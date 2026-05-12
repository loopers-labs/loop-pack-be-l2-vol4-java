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

class PasswordTest {

    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2002, 5, 11);

    @DisplayName("비밀번호 생성 시")
    @Nested
    class Of {

        @DisplayName("8~16자 ASCII 인쇄 가능 문자로 구성되고 생년월일이 포함되지 않으면 정상 생성된다")
        @Test
        void createsPassword_whenRawIsValid() {
            // given
            String raw = "Aa3!xyz@";

            // when
            Password password = assertDoesNotThrow(() -> Password.of(raw, VALID_BIRTH_DATE));

            // then
            assertThat(password.raw()).isEqualTo(raw);
        }

        @DisplayName("7자면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRawIsTooShort() {
            // given
            String raw = "Aa3!xyz";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> Password.of(raw, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("17자면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRawIsTooLong() {
            // given
            String raw = "Aa3!xyz@abcdefghi";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> Password.of(raw, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRawContainsKorean() {
            // given
            String raw = "Aa3!한글abc";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> Password.of(raw, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 전체 연도(YYYY)가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRawContainsFullYear() {
            // given
            String raw = "Aa!2002xyz";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> Password.of(raw, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 뒤 2자리(YY) 연도가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRawContainsShortYear() {
            // given
            String raw = "Aabc!@xy02";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> Password.of(raw, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 월(MM)이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRawContainsMonth() {
            // given
            String raw = "Aabc!@xy05";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> Password.of(raw, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 일(DD)이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRawContainsDay() {
            // given
            String raw = "Aabc!@xy11";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> Password.of(raw, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // given
            String raw = "Aa3!xyz@";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> Password.of(raw, null));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("raw가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRawIsNull() {
            // given
            // when
            CoreException ex = assertThrows(CoreException.class, () -> Password.of(null, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
