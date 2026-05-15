package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordPolicy passwordPolicy;

    private final PasswordHasher passwordHasher = new FakePasswordHasher();
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordPolicy, passwordHasher);
    }

    @DisplayName("회원가입할 때, ")
    @Nested
    class Signup {
        @DisplayName("로그인 ID가 중복되면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            when(userRepository.existsByLoginId("user1234")).thenReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup("user1234", "abc123!?", "홍길동", LocalDate.of(1990, 1, 15), "user@example.com");
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> verify(userRepository, never()).save(any())
            );
        }

        @DisplayName("유효한 요청이면, 비밀번호 정책 검증 후 BCrypt 해시를 저장한다.")
        @Test
        void savesUserWithHashedPassword_whenRequestIsValid() {
            // arrange
            when(userRepository.existsByLoginId("user1234")).thenReturn(false);
            when(userRepository.save(any(UserModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            UserModel result = userService.signup(
                "user1234",
                "abc123!?",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "user@example.com"
            );

            // assert
            ArgumentCaptor<UserModel> userCaptor = ArgumentCaptor.forClass(UserModel.class);
            verify(passwordPolicy).validate("abc123!?", LocalDate.of(1990, 1, 15));
            verify(userRepository).save(userCaptor.capture());

            UserModel savedUser = userCaptor.getValue();
            assertAll(
                () -> assertThat(result).isSameAs(savedUser),
                () -> assertThat(savedUser.getPasswordHash()).isEqualTo("hashed:abc123!?"),
                () -> assertThat(savedUser.getPasswordHash()).isNotEqualTo("abc123!?")
            );
        }
    }

    @DisplayName("인증할 때, ")
    @Nested
    class Authenticate {
        @DisplayName("로그인 ID 또는 비밀번호가 비어있으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenCredentialIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate("user1234", " ");
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
                () -> verify(userRepository, never()).findByLoginId(any())
            );
        }

        @DisplayName("회원을 찾을 수 없으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenUserDoesNotExist() {
            // arrange
            when(userRepository.findByLoginId("user1234")).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate("user1234", "abc123!?");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));
            when(userRepository.findByLoginId("user1234")).thenReturn(Optional.of(user));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate("user1234", "wrong123!");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하면, 회원을 반환한다.")
        @Test
        void returnsUser_whenPasswordMatches() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));
            when(userRepository.findByLoginId("user1234")).thenReturn(Optional.of(user));

            // act
            UserModel result = userService.authenticate("user1234", "abc123!?");

            // assert
            assertThat(result).isSameAs(user);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {
        @DisplayName("현재 비밀번호가 비어있으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenOldPasswordIsBlank() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));
            when(userRepository.findByLoginId("user1234")).thenReturn(Optional.of(user));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1234", " ", "new123!?");
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
                () -> verify(userRepository, never()).save(any())
            );
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenOldPasswordDoesNotMatch() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));
            when(userRepository.findByLoginId("user1234")).thenReturn(Optional.of(user));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1234", "wrong123!", "new123!?");
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
                () -> verify(userRepository, never()).save(any())
            );
        }

        @DisplayName("새 비밀번호가 정책을 통과하지 못하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordViolatesPolicy() {
            // arrange
            LocalDate birth = LocalDate.of(1990, 1, 15);
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));
            when(userRepository.findByLoginId("user1234")).thenReturn(Optional.of(user));
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "비밀번호 정책 위반"))
                .when(passwordPolicy).validate("short", birth);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1234", "abc123!?", "short");
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> verify(userRepository, never()).save(any())
            );
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsOldPassword() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));
            when(userRepository.findByLoginId("user1234")).thenReturn(Optional.of(user));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1234", "abc123!?", "abc123!?");
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> verify(userRepository, never()).save(any())
            );
        }

        @DisplayName("유효한 요청이면, 새 비밀번호 해시를 저장한다.")
        @Test
        void changesPasswordHash_whenRequestIsValid() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));
            when(userRepository.findByLoginId("user1234")).thenReturn(Optional.of(user));

            // act
            userService.changePassword("user1234", "abc123!?", "new123!?");

            // assert
            assertAll(
                () -> assertThat(user.getPasswordHash()).isEqualTo("hashed:new123!?"),
                () -> verify(passwordPolicy).validate("new123!?", user.getBirth()),
                () -> verify(userRepository).save(user)
            );
        }
    }

    private UserModel user(String loginId, String passwordHash) {
        return new UserModel(
            loginId,
            passwordHash,
            "홍길동",
            LocalDate.of(1990, 1, 15),
            loginId + "@example.com"
        );
    }

    private static class FakePasswordHasher implements PasswordHasher {
        @Override
        public String encode(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return passwordHash != null && passwordHash.equals(encode(rawPassword));
        }
    }
}
