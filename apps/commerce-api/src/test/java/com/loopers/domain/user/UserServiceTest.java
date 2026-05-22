package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final String RAW_PASSWORD = "Passw0rd!";

    private final UserValidator userValidator = mock(UserValidator.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserService userService = new UserService(userValidator, userRepository, passwordEncoder);

    private UserCommand.SignUp signUpCommand() {
        return new UserCommand.SignUp(
            "loopers01", RAW_PASSWORD, "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );
    }

    private User savedUser() {
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        userService.signUp(signUpCommand());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("회원가입하면 비밀번호가 암호화되어 저장된다")
    void signUp_encodesPassword() {
        User saved = savedUser();

        assertThat(saved.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("회원가입하면 커맨드의 정보로 사용자가 저장된다")
    void signUp_savesUserWithCommandFields() {
        User saved = savedUser();

        assertThat(saved.getLoginId()).isEqualTo("loopers01");
        assertThat(saved.getName()).isEqualTo("김루퍼");
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(1995, 3, 21));
        assertThat(saved.getEmail()).isEqualTo("looper@example.com");
    }

    @Test
    @DisplayName("검증에 실패하면 사용자를 저장하지 않는다")
    void signUp_whenValidationFails_doesNotSave() {
        doThrow(new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다."))
            .when(userValidator).validateSignUp(any());

        assertThatThrownBy(() -> userService.signUp(signUpCommand()))
            .isInstanceOf(CoreException.class);

        verify(userRepository, never()).save(any());
    }
}
