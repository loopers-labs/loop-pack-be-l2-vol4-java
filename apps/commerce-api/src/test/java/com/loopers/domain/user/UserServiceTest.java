package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private PasswordPolicy passwordPolicy;

    private final PasswordHasher passwordHasher = new FakePasswordHasher();
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(passwordPolicy, passwordHasher);
    }

    @DisplayName("회원가입할 때, ")
    @Nested
    class Signup {
        @DisplayName("로그인 ID가 중복되면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup(
                    "user1234",
                    "abc123!?",
                    "홍길동",
                    LocalDate.of(1990, 1, 15),
                    "user@example.com",
                    true
                );
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("유효한 요청이면, 비밀번호 정책 검증 후 해시된 비밀번호를 가진 회원을 생성한다.")
        @Test
        void createsUserWithHashedPassword_whenRequestIsValid() {
            // act
            UserModel result = userService.signup(
                "user1234",
                "abc123!?",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "user@example.com",
                false
            );

            // assert
            verify(passwordPolicy).validate("abc123!?", LocalDate.of(1990, 1, 15));
            assertAll(
                () -> assertThat(result.getLoginId()).isEqualTo("user1234"),
                () -> assertThat(result.getPasswordHash()).isEqualTo("hashed:abc123!?"),
                () -> assertThat(result.getPasswordHash()).isNotEqualTo("abc123!?")
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
                userService.validateCredential("user1234", " ");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate(user, "wrong123!");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하면, 인증에 성공한다.")
        @Test
        void authenticates_whenPasswordMatches() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));

            // act & assert
            userService.authenticate(user, "abc123!?");
        }

        @DisplayName("로그인 ID와 비밀번호가 있으면, 인증 헤더 검증에 성공한다.")
        @Test
        void validatesCredential_whenCredentialExists() {
            // act & assert
            userService.validateCredential("user1234", "abc123!?");
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

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, " ", "new123!?");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenOldPasswordDoesNotMatch() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, "wrong123!", "new123!?");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 정책을 통과하지 못하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordViolatesPolicy() {
            // arrange
            LocalDate birth = LocalDate.of(1990, 1, 15);
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "비밀번호 정책 위반"))
                .when(passwordPolicy).validate("short", birth);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, "abc123!?", "short");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsOldPassword() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, "abc123!?", "abc123!?");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 요청이면, 새 비밀번호 해시를 저장한다.")
        @Test
        void changesPasswordHash_whenRequestIsValid() {
            // arrange
            UserModel user = user("user1234", passwordHasher.encode("abc123!?"));

            // act
            userService.changePassword(user, "abc123!?", "new123!?");

            // assert
            assertAll(
                () -> assertThat(user.getPasswordHash()).isEqualTo("hashed:new123!?"),
                () -> verify(passwordPolicy).validate("new123!?", user.getBirth())
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
