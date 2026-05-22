package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

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

    private User existingUser() {
        return User.create(
            "loopers01", passwordEncoder.encode(RAW_PASSWORD), "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );
    }

    @Test
    @DisplayName("올바른 자격으로 인증하면 userId를 반환한다")
    void authenticate_withValidCredentials_returnsUserId() {
        User user = existingUser();
        when(userRepository.findByLoginId("loopers01")).thenReturn(Optional.of(user));

        Optional<Long> result = userService.authenticate("loopers01", RAW_PASSWORD);

        assertThat(result).contains(user.getId());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 빈 값을 반환한다")
    void authenticate_withWrongPassword_returnsEmpty() {
        when(userRepository.findByLoginId("loopers01")).thenReturn(Optional.of(existingUser()));

        Optional<Long> result = userService.authenticate("loopers01", "WrongPass1!");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 로그인 ID이면 빈 값을 반환한다")
    void authenticate_withUnknownLoginId_returnsEmpty() {
        when(userRepository.findByLoginId("unknown01")).thenReturn(Optional.empty());

        Optional<Long> result = userService.authenticate("unknown01", RAW_PASSWORD);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("userId로 내 정보를 조회하면 해당 사용자를 반환한다")
    void getInfo_returnsUser() {
        User user = existingUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.getInfo(1L);

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("사용자가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
    void getInfo_whenUserNotFound_throwsNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getInfo(999L))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }
}
