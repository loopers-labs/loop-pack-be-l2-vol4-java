package com.loopers.user.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    // NoOp 구현체: encrypt는 그대로 반환, matches는 문자열 동등 비교
    private final PasswordEncryptor noOpEncryptor = new PasswordEncryptor() {
        @Override
        public String encrypt(String rawPassword) { return rawPassword; }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return rawPassword.equals(encodedPassword);
        }
    };

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(noOpEncryptor);
    }

    @DisplayName("getOrThrow를 호출할 때,")
    @Nested
    class GetOrThrow {

        @DisplayName("사용자가 존재하면, 해당 사용자를 반환한다.")
        @Test
        void returnsUser_whenUserExists() {
            // arrange
            UserModel user = new UserModel("user1", "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE);

            // act
            UserModel result = userService.getOrThrow(Optional.of(user));

            // assert
            assertThat(result).isEqualTo(user);
        }

        @DisplayName("사용자가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.getOrThrow(Optional.empty())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class SignUp {

        @DisplayName("이미 존재하는 loginId이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            UserModel existing = new UserModel("user1", "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE);
            UserModel newUser = new UserModel("user1", "Pass456@", "김길동", "other@example.com", "1990-05-15", Gender.FEMALE);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.signUp(Optional.of(existing), newUser)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("새로운 loginId이면, 비밀번호가 인코딩된 UserModel을 반환한다.")
        @Test
        void returnsEncodedUser_whenLoginIdIsNew() {
            // arrange
            String rawPassword = "Pass123!";
            UserModel newUser = new UserModel("user1", rawPassword, "홍길동", "test@example.com", "2000-01-01", Gender.MALE);

            // act
            UserModel result = userService.signUp(Optional.empty(), newUser);

            // assert
            assertThat(noOpEncryptor.matches(rawPassword, result.getPassword())).isTrue();
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호와 동일한 새 비밀번호이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String rawPassword = "Pass123!";
            UserModel user = new UserModel("user1", rawPassword, "홍길동", "test@example.com", "2000-01-01", Gender.MALE);
            userService.signUp(Optional.empty(), user); // 비밀번호 인코딩

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.changePassword(user, rawPassword)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("새로운 비밀번호이면, 비밀번호가 인코딩되어 변경된다.")
        @Test
        void changesEncodedPassword_whenNewPasswordIsDifferent() {
            // arrange
            String rawPassword = "Pass123!";
            String newPassword = "NewPass1!";
            UserModel user = new UserModel("user1", rawPassword, "홍길동", "test@example.com", "2000-01-01", Gender.MALE);
            userService.signUp(Optional.empty(), user); // 비밀번호 인코딩

            // act
            userService.changePassword(user, newPassword);

            // assert
            assertThat(noOpEncryptor.matches(newPassword, user.getPassword())).isTrue();
        }
    }
}
