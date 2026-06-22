package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginIdTest {

    @DisplayName("LoginId 생성 시,")
    @Nested
    class Create {

        @DisplayName("영문+숫자 조합이면 정상 생성된다.")
        @Test
        void creates_whenAlphanumeric() {
            // arrange
            String value = "user01";

            // act
            LoginId loginId = new LoginId(value);

            // assert
            assertThat(loginId.value()).isEqualTo(value);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> new LoginId(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBlank() {
            // arrange & act & assert
            assertThatThrownBy(() -> new LoginId("   "))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("특수문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenContainsSpecialCharacter() {
            // arrange & act & assert
            assertThatThrownBy(() -> new LoginId("user@01"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenContainsKorean() {
            // arrange & act & assert
            assertThatThrownBy(() -> new LoginId("유저01"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
