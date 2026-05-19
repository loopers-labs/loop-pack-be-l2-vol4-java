package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginIdTest {

    @DisplayName("로그인 ID 생성 시")
    @Nested
    class Create {

        @DisplayName("영문/숫자로 구성된 8~16자면 정상 생성된다")
        @Test
        void createsLoginId_whenValueIsValid() {
            // given
            String value = "loopers01";

            // when
            LoginId loginId = new LoginId(value);

            // then
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        @DisplayName("8자 미만이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueIsShorterThanEightCharacters() {
            // given
            String value = "loop123";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new LoginId(value));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("16자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueIsLongerThanSixteenCharacters() {
            // given
            String value = "abcdefghij1234567";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new LoginId(value));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문/숫자가 아닌 문자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueContainsNonAlphanumeric() {
            // given
            String value = "loopers@01";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new LoginId(value));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // given
            // when
            CoreException ex = assertThrows(CoreException.class, () -> new LoginId(null));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
