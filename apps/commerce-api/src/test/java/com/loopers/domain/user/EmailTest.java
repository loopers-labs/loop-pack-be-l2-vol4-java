package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class EmailTest {

    @DisplayName("Email을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("올바른 이메일 형식이면 입력값을 그대로 보존한 Email이 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"kyle@example.com", "user+tag@domain.co.kr", "a@b.com"})
        void createsEmail_whenValueIsValidEmailFormat(String value) {
            // act
            Email email = Email.from(value);

            // assert
            assertThat(email.value()).isEqualTo(value);
        }

        @DisplayName("올바른 이메일 형식이 아니면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"notanemail", "missing.domain@", "@nodomain.com", "two@@signs.com"})
        void throwsBadRequest_whenValueIsInvalidEmailFormat(String value) {
            // act & assert
            assertThatThrownBy(() -> Email.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("길이가 254자를 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueExceedsMaxLength() {
            // arrange
            String tooLongEmail = "a".repeat(243) + "@example.com";

            // act & assert
            assertThatThrownBy(() -> Email.from(tooLongEmail))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이거나 빈 문자열이거나 공백 문자(스페이스, 탭, 개행 등)로만 이루어진 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "      ", "\t", "\n", "\r"})
        void throwsBadRequest_whenValueIsNullOrBlank(String value) {
            // act & assert
            assertThatThrownBy(() -> Email.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
