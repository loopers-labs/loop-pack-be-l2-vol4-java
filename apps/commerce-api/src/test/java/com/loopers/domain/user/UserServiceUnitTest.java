package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @DisplayName("회원가입을 할 때")
    @Nested
    class CreateUser {

        @DisplayName("정상 입력이면, 비밀번호를 암호화하여 저장한다.")
        @Test
        void encodesPasswordAndSaves_whenValidInput() {
            // given
            String loginId = "minbo";
            String rawPassword = "Test1234!";
            String name = "민보";
            LocalDate birthDate = LocalDate.of(1991, 8, 21);
            String email = "test@example.com";
            String encodedPassword = "ENCODED";

            given(userRepository.existsByLoginId(loginId)).willReturn(false);
            given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
            given(userRepository.save(any(UserModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            UserModel result = userService.createUser(loginId, rawPassword, name, birthDate, email);

            // then
            verify(passwordEncoder).encode(rawPassword);
            verify(userRepository).save(any(UserModel.class));
            assertThat(result.getLoginId()).isEqualTo(loginId);
            assertThat(result.getPassword()).isEqualTo(encodedPassword);
        }

        @DisplayName("이미 사용중인 loginId가 존재한다면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdExists() {
            // given
            given(userRepository.existsByLoginId("minbo")).willReturn(true);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.createUser("minbo", "Pass1234!", "민보", LocalDate.of(2000, 1, 1), "min@example.com")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository, never()).save(any());
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // given
            String passwordContainingBirth = "P20000101!";
            LocalDate birthDate = LocalDate.of(2000, 1, 1);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.createUser("minbo", passwordContainingBirth, "민보", birthDate, "min@example.com")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(userRepository, never()).existsByLoginId(anyString());
            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository, never()).save(any());
        }
    }

    @DisplayName("비밀번호를 변경할 때")
    @Nested
    class ChangePassword {

        private final String loginId = "minbo";
        private final String currentRaw = "Test1234!";
        private final String newRaw = "NewPass5678!";
        private final String encodedCurrent = "ENCODED_CURRENT";
        private final String encodedNew = "ENCODED_NEW";

        @DisplayName("정상 입력이면, 새 비밀번호가 암호화되어 저장된다.")
        @Test
        void encodesNewPasswordAndUpdates_whenValidInput() {
            // given
            UserModel user = UserModel.of(loginId, encodedCurrent, "민보", LocalDate.of(1991, 8, 21), "test@example.com");
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(currentRaw, encodedCurrent)).willReturn(true);
            given(passwordEncoder.matches(newRaw, encodedCurrent)).willReturn(false);
            given(passwordEncoder.encode(newRaw)).willReturn(encodedNew);

            // when
            UserModel result = userService.changePassword(loginId, currentRaw, newRaw);

            // then
            verify(passwordEncoder).encode(newRaw);
            assertThat(result.getPassword()).isEqualTo(encodedNew);
        }

        @DisplayName("존재하지 않는 loginId면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserNotFound() {
            // given
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.changePassword(loginId, currentRaw, newRaw)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(passwordEncoder, never()).encode(anyString());
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordMismatch() {
            // given
            UserModel user = UserModel.of(loginId, encodedCurrent, "민보", LocalDate.of(1991, 8, 21), "test@example.com");
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(currentRaw, encodedCurrent)).willReturn(false);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.changePassword(loginId, currentRaw, newRaw)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(passwordEncoder, never()).encode(anyString());
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordSameAsCurrent() {
            // given
            UserModel user = UserModel.of(loginId, encodedCurrent, "민보", LocalDate.of(1991, 8, 21), "test@example.com");
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(currentRaw, encodedCurrent)).willReturn(true);
            given(passwordEncoder.matches(currentRaw, encodedCurrent)).willReturn(true);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.changePassword(loginId, currentRaw, currentRaw)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(passwordEncoder, never()).encode(anyString());
        }
    }
}
