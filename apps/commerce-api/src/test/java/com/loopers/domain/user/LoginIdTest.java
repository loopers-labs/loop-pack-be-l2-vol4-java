package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class LoginIdTest {

    @DisplayName("LoginId를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("영문 대소문자와 숫자로 이루어진 4~20자 문자열을 주면, 입력값을 그대로 보존한 LoginId가 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"abcd", "kyleKim123", "kyleKim2026Champion1"})
        void createsLoginId_whenValueIsAlphanumericWithinLengthBounds(String value) {
            // act
            LoginId loginId = LoginId.from(value);

            // assert
            assertThat(loginId.value()).isEqualTo(value);
        }

        @DisplayName("길이가 4자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"a", "abc"})
        void throwsBadRequest_whenValueIsShorterThanMinLength(String value) {
            // act & assert
            assertThatThrownBy(() -> LoginId.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("길이가 20자 초과면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"abcdefghijklmnopqrstu", "abcdefghijklmnopqrstuvwxyz1234"})
        void throwsBadRequest_whenValueIsLongerThanMaxLength(String value) {
            // act & assert
            assertThatThrownBy(() -> LoginId.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문 대소문자와 숫자 외의 문자(한글, 특수문자, 공백)가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"가나다라", "abc!def", "abc def"})
        void throwsBadRequest_whenValueContainsNonAlphanumericCharacters(String value) {
            // act & assert
            assertThatThrownBy(() -> LoginId.from(value))
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
            assertThatThrownBy(() -> LoginId.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
