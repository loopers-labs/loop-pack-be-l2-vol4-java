package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlainPasswordTest {

    @DisplayName("PlainPassword 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("8~16자의 영문/숫자/특수문자로만 이뤄지고 생년월일을 포함하지 않으면, 통과한다.")
        @Test
        void createsPlainPassword_whenPasswordIsValid() {
            // arrange
            String validPassword = "Loopers!2026";
            BirthDate birthDate = BirthDate.of(LocalDate.of(1990, 5, 11));

            // act / assert
            assertThatCode(() -> PlainPassword.of(validPassword, birthDate))
                .doesNotThrowAnyException();
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsShorterThan8() {
            // arrange
            String shortPassword = "Aa1!56";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PlainPassword.of(shortPassword, BirthDate.of(LocalDate.of(1990, 5, 11)));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsLongerThan16() {
            // arrange
            String longPassword = "Aa1!56789012345678";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PlainPassword.of(longPassword, BirthDate.of(LocalDate.of(1990, 5, 11)));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 한글 등 허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordHasInvalidCharacters() {
            // arrange
            String hangulPassword = "Loopers한글!2026";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PlainPassword.of(hangulPassword, BirthDate.of(LocalDate.of(1990, 5, 11)));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDateBasic() {
            // arrange
            BirthDate birthDate = BirthDate.of(LocalDate.of(1990, 5, 11));
            String passwordWithBirth = "A!19900511aa";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PlainPassword.of(passwordWithBirth, birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyyy-MM-dd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDateIso() {
            // arrange
            BirthDate birthDate = BirthDate.of(LocalDate.of(1990, 5, 11));
            String passwordWithBirth = "A!1990-05-11";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                PlainPassword.of(passwordWithBirth, birthDate);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

}
