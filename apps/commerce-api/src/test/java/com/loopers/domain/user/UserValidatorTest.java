package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserValidatorTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserValidator userValidator = new UserValidator(userRepository);

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
}
