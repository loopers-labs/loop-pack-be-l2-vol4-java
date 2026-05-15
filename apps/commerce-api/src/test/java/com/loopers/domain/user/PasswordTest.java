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
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordTest {

    private final PasswordEncryptor passwordEncryptor = new FakePasswordEncryptor("encrypted:");

    @DisplayName("Password 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상적으로 암호화되어 생성된다.")
        @Test
        void createsPassword() {
            // given
            String rawPassword = "Password";
            BirthDate birthDate = new BirthDate("1998-06-12");

            // when
            Password password = new Password(rawPassword, birthDate, passwordEncryptor);

            // then
            assertThat(password.getValue()).isEqualTo("encrypted:" + rawPassword);
        }

        @DisplayName("8~16자 영문/숫자/특수문자가 아니면 예외가 발생한다.")
        @NullAndEmptySource
        @ValueSource(strings = {"Pw1234!", "PasswordPasswordP", "한글Password1!", "Password 1!"})
        @ParameterizedTest
        void throwsBadRequestException_whenRawPasswordIsInvalid(String rawPassword) {
            // given
            BirthDate birthDate = new BirthDate("1998-06-12");

            // when
            CoreException result = assertThrows(CoreException.class, () -> new Password(rawPassword, birthDate, passwordEncryptor));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 포함되어 있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenRawPasswordContainsBirthDate() {
            // given
            String rawPassword = "Pass19900101!";
            BirthDate birthDate = new BirthDate("1990-01-01");

            // when
            CoreException result = assertThrows(CoreException.class, () -> new Password(rawPassword, birthDate, passwordEncryptor));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
