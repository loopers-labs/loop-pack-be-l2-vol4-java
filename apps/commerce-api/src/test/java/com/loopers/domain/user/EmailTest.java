package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @DisplayName("Email 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> new Email(null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 문자열 또는 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void throwsBadRequest_whenValueIsBlank(String value) {
            CoreException result = assertThrows(CoreException.class, () -> new Email(value));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 포맷에 맞지 않으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"abc", "abc@", "@a.com", "abc@a", "abc@.com", "abc@a.", "abc @a.com"})
        void throwsBadRequest_whenValueDoesNotMatchEmailFormat(String value) {
            CoreException result = assertThrows(CoreException.class, () -> new Email(value));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정상 이메일 포맷이면 정상적으로 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"kim@loopers.com", "abc.def@sub.example.co.kr", "user+tag@example.io"})
        void createsEmail_whenValueIsValid(String value) {
            Email email = new Email(value);

            assertThat(email.getValue()).isEqualTo(value);
        }
    }
}
