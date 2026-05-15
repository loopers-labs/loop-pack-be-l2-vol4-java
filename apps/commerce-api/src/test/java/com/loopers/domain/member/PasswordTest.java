package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @DisplayName("비밀번호를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 비밀번호가 주어지면, 암호화된 값으로 생성된다.")
        @Test
        void createsPassword_whenValid() {
            // Arrange
            String rawPassword = "Password1!";
            String birthDate = "1990-01-01";

            // Act
            Password password = Password.of(rawPassword, birthDate, encoder);

            // Assert
            assertThat(password.matches(rawPassword, encoder)).isTrue();
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // Arrange
            String shortPassword = "Pass1!";

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                Password.of(shortPassword, "1990-01-01", encoder)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자 초과면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // Arrange
            String longPassword = "Password1!Password1!";

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                Password.of(longPassword, "1990-01-01", encoder)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(하이픈 포함)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // Arrange
            String birthDate = "1990-01-01";
            String passwordWithBirth = "1990-01-01Ab!";

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                Password.of(passwordWithBirth, birthDate, encoder)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(하이픈 제외)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDateWithoutHyphen() {
            // Arrange
            String birthDate = "1990-01-01";
            String passwordWithBirth = "19900101Ab!";

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                Password.of(passwordWithBirth, birthDate, encoder)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class Change {

        @DisplayName("올바른 정보가 주어지면, 새 비밀번호로 암호화된 Password가 반환된다.")
        @Test
        void returnsNewPassword_whenCredentialsAreValid() {
            // Arrange
            String birthDate = "1990-01-01";
            Password current = Password.of("Password1!", birthDate, encoder);

            // Act
            Password changed = current.change("Password1!", "NewPassword2@", birthDate, encoder);

            // Assert
            assertThat(changed.matches("NewPassword2@", encoder)).isTrue();
        }

        @DisplayName("기존 비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenOldPasswordIsWrong() {
            // Arrange
            String birthDate = "1990-01-01";
            Password current = Password.of("Password1!", birthDate, encoder);

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                current.change("WrongPassword1!", "NewPassword2@", birthDate, encoder)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // Arrange
            String birthDate = "1990-01-01";
            Password current = Password.of("Password1!", birthDate, encoder);

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                current.change("Password1!", "Password1!", birthDate, encoder)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
