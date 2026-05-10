package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @DisplayName("이메일을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 형식이면, 정상적으로 생성된다.")
        @Test
        void createsEmail_whenFormatValid() {
            // arrange
            String value = "user@example.com";

            // act
            Email email = new Email(value);

            // assert
            assertAll(
                () -> assertThat(email).isNotNull(),
                () -> assertThat(email.value()).isEqualTo(value)
            );
        }

        @DisplayName("값이 null 이거나 공백으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void throwsBadRequest_whenValueIsNullOrBlank(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Email(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "userexample.com",
            "user@@example.com",
            "user@examplecom",
            "@example.com",
            "user@",
            "user @example.com",
            "유저@example.com",
            "user@example.c"
        })
        void throwsBadRequest_whenFormatInvalid(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Email(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("254자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLengthExceeds254() {
            // arrange — local 249자 + "@e.com"(6자) = 255자
            String value = "a".repeat(249) + "@e.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> new Email(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
