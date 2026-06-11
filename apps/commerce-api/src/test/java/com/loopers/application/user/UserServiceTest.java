package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final String RAW_PASSWORD = "Passw0rd!";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserReader userReader = mock(UserReader.class);
    private final UserService userService = new UserService(userRepository, passwordEncoder, userReader);

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
    @DisplayName("이미 존재하는 로그인 ID로 회원가입하면 CONFLICT 예외가 발생하고 저장하지 않는다")
    void signUp_whenLoginIdAlreadyExists_throwsConflictAndDoesNotSave() {
        when(userRepository.existsByLoginId("loopers01")).thenReturn(true);

        assertThatThrownBy(() -> userService.signUp(signUpCommand()))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("생년월일이 포함된 비밀번호로 회원가입하면 BAD_REQUEST 예외가 발생하고 저장하지 않는다")
    void signUp_whenPasswordContainsBirthDate_throwsBadRequestAndDoesNotSave() {
        UserCommand.SignUp command = new UserCommand.SignUp(
            "loopers01", "19950321aA", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );

        assertThatThrownBy(() -> userService.signUp(command))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);

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
    @DisplayName("비밀번호 수정 시 새 비밀번호가 인코딩되어 갱신된다")
    void changePassword_updatesPasswordToEncoded() {
        User user = existingUser();
        when(userReader.get(1L)).thenReturn(user);
        String newRawPassword = "NewPass1!";
        UserCommand.ChangePassword command = new UserCommand.ChangePassword(1L, RAW_PASSWORD, newRawPassword);

        userService.changePassword(command);

        assertThat(user.getPassword()).isNotEqualTo(newRawPassword);
        assertThat(passwordEncoder.matches(newRawPassword, user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("비밀번호 수정 시 사용자가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
    void changePassword_whenUserNotFound_throwsNotFound() {
        when(userReader.get(999L)).thenThrow(new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        UserCommand.ChangePassword command = new UserCommand.ChangePassword(999L, RAW_PASSWORD, "NewPass1!");

        assertThatThrownBy(() -> userService.changePassword(command))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호 수정 시 현재 비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다")
    void changePassword_whenCurrentPasswordMismatches_throwsBadRequest() {
        User user = existingUser();
        when(userReader.get(1L)).thenReturn(user);
        UserCommand.ChangePassword command = new UserCommand.ChangePassword(1L, "WrongPass1!", "NewPass1!");

        assertThatThrownBy(() -> userService.changePassword(command))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("비밀번호 수정 시 새 비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다")
    void changePassword_whenNewPasswordContainsBirthDate_throwsBadRequest() {
        User user = existingUser();
        when(userReader.get(1L)).thenReturn(user);
        UserCommand.ChangePassword command = new UserCommand.ChangePassword(1L, RAW_PASSWORD, "19950321aA");

        assertThatThrownBy(() -> userService.changePassword(command))
            .isInstanceOf(CoreException.class)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
    }
}
