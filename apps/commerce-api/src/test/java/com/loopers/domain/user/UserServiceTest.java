package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    @DisplayName("유저를 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("존재하는 로그인 ID를 주면, 해당 유저를 반환한다.")
        @Test
        void returnsUser_whenUserIdExists() {
            // arrange
            UserModel user = new UserModel(
                    "user1",
                    "Password1!",
                    "홍길동",
                    LocalDate.of(1990, 1, 1),
                    "user1@example.com"
            );
            FakeUserRepository userRepository = new FakeUserRepository();
            userRepository.save(user);
            UserService userService = new UserService(userRepository);

            // act
            UserModel result = userService.getUser("user1", "Password1!");

            // assert
            assertThat(result).isSameAs(user);
        }

        @DisplayName("존재하지 않는 로그인 ID를 주면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsUserNotFound_whenUserIdDoesNotExist() {
            // arrange
            UserRepository userRepository = new FakeUserRepository();
            UserService userService = new UserService(userRepository);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getUser("unknown", "Password1!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, PASSWORD_MISMATCH 예외가 발생한다.")
        @Test
        void throwsPasswordMismatch_whenPasswordDoesNotMatch() {
            // arrange
            UserModel user = new UserModel(
                    "user1",
                    "Password1!",
                    "홍길동",
                    LocalDate.of(1990, 1, 1),
                    "user1@example.com"
            );
            FakeUserRepository userRepository = new FakeUserRepository();
            userRepository.save(user);
            UserService userService = new UserService(userRepository);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getUser("user1", "Wrong1!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호와 새 비밀번호가 유효하면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenPasswordsAreValid() {
            // arrange
            UserModel user = new UserModel(
                    "user1",
                    "Password1!",
                    "홍길동",
                    LocalDate.of(1990, 1, 1),
                    "user1@example.com"
            );
            FakeUserRepository userRepository = new FakeUserRepository();
            userRepository.save(user);
            UserService userService = new UserService(userRepository);

            // act
            UserModel result = userService.changePassword("user1", "Password1!", "Password2!");

            // assert
            assertThat(result).isSameAs(user);
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1", "Password1!", "Password3!");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
        }

        @DisplayName("존재하지 않는 로그인 ID를 주면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsUserNotFound_whenUserIdDoesNotExist() {
            // arrange
            UserRepository userRepository = new FakeUserRepository();
            UserService userService = new UserService(userRepository);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("unknown", "Password1!", "Password2!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
        }
    }

    private static class FakeUserRepository implements UserRepository {
        private final Map<String, UserModel> users = new HashMap<>();

        @Override
        public UserModel save(UserModel user) {
            users.put(user.getUserId(), user);
            return user;
        }

        @Override
        public Optional<UserModel> findByUserId(String userId) {
            return Optional.ofNullable(users.get(userId));
        }
    }
}
