package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncrypter passwordEncrypter;

    @InjectMocks
    private UserService userService;

    @DisplayName("회원가입을 처리할 때,")
    @Nested
    class SignUp {

        private final String rawLoginId = "kyleKim";
        private final String rawPassword = "Kyle!2030";
        private final String rawName = "김카일";
        private final LocalDate rawBirthDate = LocalDate.of(1995, 3, 21);
        private final String rawEmail = "kyle@example.com";

        @DisplayName("신규 로그인 ID와 이메일이면 회원가입에 성공한다.")
        @Test
        void returnsSavedUser_whenLoginIdAndEmailAreAvailable() {
            // arrange
            given(userRepository.existsByLoginId(rawLoginId)).willReturn(false);
            given(userRepository.existsByEmail(rawEmail)).willReturn(false);
            given(userRepository.save(any(UserModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            UserModel signedUpUser = userService.signUp(rawLoginId, rawPassword, rawName, rawBirthDate, rawEmail);

            // assert
            assertAll(
                () -> assertThat(signedUpUser.getLoginId()).isEqualTo(LoginId.from(rawLoginId)),
                () -> assertThat(signedUpUser.getEmail()).isEqualTo(Email.from(rawEmail)),
                () -> verify(userRepository).existsByLoginId(rawLoginId),
                () -> verify(userRepository).existsByEmail(rawEmail),
                () -> verify(userRepository).save(any(UserModel.class))
            );
        }

        @DisplayName("이미 사용 중인 로그인 ID면 회원가입에 실패한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            given(userRepository.existsByLoginId(rawLoginId)).willReturn(true);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> userService.signUp(rawLoginId, rawPassword, rawName, rawBirthDate, rawEmail))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> verify(userRepository, never()).save(any(UserModel.class))
            );
        }

        @DisplayName("이미 사용 중인 이메일이면 회원가입에 실패한다.")
        @Test
        void throwsConflict_whenEmailAlreadyExists() {
            // arrange
            given(userRepository.existsByLoginId(rawLoginId)).willReturn(false);
            given(userRepository.existsByEmail(rawEmail)).willReturn(true);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> userService.signUp(rawLoginId, rawPassword, rawName, rawBirthDate, rawEmail))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> verify(userRepository, never()).save(any(UserModel.class))
            );
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class ReadMyInfo {

        @DisplayName("저장된 회원이면 해당 UserModel을 반환한다.")
        @Test
        void returnsUser_whenUserIdIsRegistered() {
            // arrange
            Long userId = 1L;
            UserModel storedUser = mock(UserModel.class);
            given(userRepository.findById(userId)).willReturn(Optional.of(storedUser));

            // act
            UserModel readUser = userService.readMyInfo(userId);

            // assert
            assertThat(readUser).isSameAs(storedUser);
        }

        @DisplayName("저장되지 않은 회원이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserIdIsNotRegistered() {
            // arrange
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> userService.readMyInfo(userId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("저장된 회원이면 currentRawPassword와 newRawPassword를 그대로 위임한다.")
        @Test
        void delegatesToUser_whenUserIdIsRegistered() {
            // arrange
            Long userId = 1L;
            String currentRawPassword = "Kyle!2030";
            String newRawPassword = "Newer!2031";
            UserModel user = mock(UserModel.class);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // act
            userService.changePassword(userId, currentRawPassword, newRawPassword);

            // assert
            verify(user).changePassword(currentRawPassword, newRawPassword, passwordEncrypter);
        }

        @DisplayName("저장되지 않은 회원이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserIdIsNotRegistered() {
            // arrange
            Long userId = 999L;
            String currentRawPassword = "Kyle!2030";
            String newRawPassword = "Newer!2031";
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(userId, currentRawPassword, newRawPassword))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
