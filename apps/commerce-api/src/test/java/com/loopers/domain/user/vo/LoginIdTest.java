package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginIdTest {

    @DisplayName("LoginId.tryParse 호출 시, ")
    @Nested
    class TryParse {

        @DisplayName("유효한 값을 주면, 해당 LoginId 가 담긴 Optional 을 반환한다.")
        @Test
        void returnsLoginId_whenValueIsValid() {
            // arrange
            String raw = "loopers01";

            // act
            Optional<LoginId> result = LoginId.tryParse(raw);

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().value()).isEqualTo(raw);
        }

        @DisplayName("형식에 맞지 않는 값을 주면, 빈 Optional 을 반환한다.")
        @Test
        void returnsEmpty_whenValueIsInvalid() {
            // act & assert
            assertThat(LoginId.tryParse("loopers!")).isEmpty();
            assertThat(LoginId.tryParse("loopers012345678901234")).isEmpty();
            assertThat(LoginId.tryParse(null)).isEmpty();
        }
    }

    @DisplayName("LoginId 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("영문/숫자 외의 문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsInvalidCharacters() {
            // arrange
            String invalid = "loopers!";

            // act
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(invalid));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("20자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsLongerThan20() {
            // arrange
            String tooLong = "loopers012345678901234";

            // act
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(tooLong));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
