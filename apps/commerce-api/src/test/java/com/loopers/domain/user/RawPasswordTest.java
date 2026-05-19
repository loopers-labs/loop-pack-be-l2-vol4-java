package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RawPasswordTest {

    @DisplayName("RawPassword 생성 시")
    @Nested
    class Create {

        @DisplayName("8~16자 ASCII 인쇄 가능 문자로 구성되면 정상 생성된다")
        @Test
        void createsRawPassword_whenInputIsValid() {
            // given
            String raw = "Aa3!xyz@";

            // when
            RawPassword password = assertDoesNotThrow(() -> new RawPassword(raw));

            // then
            assertThat(password.value()).isEqualTo(raw);
        }

        @DisplayName("7자면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenInputIsTooShort() {
            // given
            String raw = "Aa3!xyz";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new RawPassword(raw));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("17자면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenInputIsTooLong() {
            // given
            String raw = "Aa3!xyz@abcdefghi";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new RawPassword(raw));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenInputContainsKorean() {
            // given
            String raw = "Aa3!한글abc";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new RawPassword(raw));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenInputIsNull() {
            // given
            // when
            CoreException ex = assertThrows(CoreException.class, () -> new RawPassword(null));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
