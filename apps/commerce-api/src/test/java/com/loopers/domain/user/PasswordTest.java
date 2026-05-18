package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordTest {

    @DisplayName("비밀번호를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("8~16자의 영문/숫자/특수문자 조합이면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenValueIsValid() {
            // arrange
            String value = "Pass1234!";

            // act
            Password password = new Password(value);

            // assert
            assertAll(
                () -> assertThat(password).isNotNull(),
                () -> assertThat(password.value()).isEqualTo(value)
            );
        }

        @DisplayName("값이 null 이거나 공백으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void throwsBadRequest_whenValueIsNullOrBlank(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Password(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("길이가 8~16자 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "Pass12!",
            "Pass1234!12345678"
        })
        void throwsBadRequest_whenLengthIsOutOfRange(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Password(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문/숫자/특수문자 외 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "Pass1234한1",
            "Pass 1234!",
            "Pass1234\t!"
        })
        void throwsBadRequest_whenContainsDisallowedCharacter(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Password(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Password.of 로 생년월일과 함께 생성할 때, ")
    @Nested
    class CreateWithBirth {

        @DisplayName("정상 비밀번호이고 생년월일을 포함하지 않으면, 정상 생성된다.")
        @Test
        void createsPassword_whenValid() {
            // arrange
            String raw = "Pass1234!";
            Birth birth = new Birth(LocalDate.of(1990, 1, 1));

            // act
            Password password = Password.of(raw, birth);

            // assert
            assertAll(
                () -> assertThat(password).isNotNull(),
                () -> assertThat(password.value()).isEqualTo(raw)
            );
        }

        @DisplayName("생년월일(yyyyMMdd)을 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenContainsBirth() {
            // arrange
            String raw = "Pass19900101!";
            Birth birth = new Birth(LocalDate.of(1990, 1, 1));

            // act
            CoreException result = assertThrows(CoreException.class, () -> Password.of(raw, birth));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
