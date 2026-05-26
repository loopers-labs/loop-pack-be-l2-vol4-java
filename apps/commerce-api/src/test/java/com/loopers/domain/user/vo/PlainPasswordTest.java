package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlainPasswordTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 1);

    @DisplayName("PlainPassword 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 비밀번호면 정상 생성된다.")
        @Test
        void creates_whenValid() {
            // arrange
            String value = "Password1!";

            // act
            PlainPassword password = new PlainPassword(value, BIRTH_DATE);

            // assert
            assertThat(password.value()).isEqualTo(value);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> new PlainPassword(null, BIRTH_DATE))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("8자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTooShort() {
            // arrange & act & assert
            assertThatThrownBy(() -> new PlainPassword("Pass1!", BIRTH_DATE))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("16자 초과면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTooLong() {
            // arrange & act & assert
            assertThatThrownBy(() -> new PlainPassword("Password1!Password1!", BIRTH_DATE))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("허용되지 않는 문자(공백 등)가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenContainsDisallowedCharacter() {
            // arrange & act & assert
            assertThatThrownBy(() -> new PlainPassword("Pass word1!", BIRTH_DATE))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 8자리(yyyyMMdd)를 포함하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenContainsBirthDate8() {
            // arrange
            String password = "19900101!A";  // yyyyMMdd 포함

            // act & assert
            assertThatThrownBy(() -> new PlainPassword(password, BIRTH_DATE))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 6자리(yyMMdd)를 포함하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenContainsBirthDate6() {
            // arrange
            String password = "Pass900101";  // yyMMdd 포함

            // act & assert
            assertThatThrownBy(() -> new PlainPassword(password, BIRTH_DATE))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 4자리(MMdd)를 포함하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenContainsBirthDate4() {
            // arrange
            String password = "Password0101";  // MMdd 포함

            // act & assert
            assertThatThrownBy(() -> new PlainPassword(password, BIRTH_DATE))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
