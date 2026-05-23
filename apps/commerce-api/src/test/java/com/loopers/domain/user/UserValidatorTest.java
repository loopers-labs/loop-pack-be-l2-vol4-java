package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserValidatorTest {

    private static final String RAW_PASSWORD = "Passw0rd!";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 21);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserValidator userValidator = new UserValidator(userRepository, passwordEncoder);

    private UserCommand.SignUp signUpCommand() {
        return new UserCommand.SignUp(
            "loopers01", "Passw0rd!", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );
    }

    @Test
    @DisplayName("이미 존재하는 로그인 ID이면 CONFLICT 예외가 발생한다")
    void duplicateLoginId_throwsConflict() {
        when(userRepository.existsByLoginId("loopers01")).thenReturn(true);

        assertThatThrownBy(() -> userValidator.validateSignUp(signUpCommand()))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("존재하지 않는 로그인 ID이면 예외 없이 통과한다")
    void newLoginId_passes() {
        when(userRepository.existsByLoginId("loopers01")).thenReturn(false);

        assertThatCode(() -> userValidator.validateSignUp(signUpCommand()))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비밀번호가 사용자의 생년월일을 포함하면 BAD_REQUEST 예외가 발생한다")
    void passwordContainingBirthDate_throwsBadRequest() {
        UserCommand.SignUp command = new UserCommand.SignUp(
            "loopers01", "19950321aA", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );

        assertThatThrownBy(() -> userValidator.validateSignUp(command))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
    }

    private User existingUser() {
        return User.create(
            "loopers01", passwordEncoder.encode(RAW_PASSWORD), "김루퍼", BIRTH_DATE, "looper@example.com"
        );
    }

    @Test
    @DisplayName("비밀번호 변경 검증: 올바른 현재 비밀번호 + 유효한 새 비밀번호이면 예외 없이 통과한다")
    void validateChangePassword_passes_whenValid() {
        User user = existingUser();

        assertThatCode(() -> userValidator.validateChangePassword(user, RAW_PASSWORD, "NewPass1!"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비밀번호 변경 검증: 현재 비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다")
    void validateChangePassword_throwsBadRequest_whenCurrentPasswordMismatches() {
        User user = existingUser();

        assertThatThrownBy(() -> userValidator.validateChangePassword(user, "WrongPass1!", "NewPass1!"))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("비밀번호 변경 검증: 새 비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다")
    void validateChangePassword_throwsBadRequest_whenNewPasswordContainsBirthDate() {
        User user = existingUser();

        assertThatThrownBy(() -> userValidator.validateChangePassword(user, RAW_PASSWORD, "19950321aA"))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
    }
}
