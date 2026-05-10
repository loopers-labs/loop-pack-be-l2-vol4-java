package com.loopers.domain.user;

import com.loopers.CommerceApiTestApplication;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
    classes = CommerceApiTestApplication.class,
    properties = {
        "spring.profiles.active=test",
        "datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/loopers_test",
        "datasource.mysql-jpa.main.username=application",
        "datasource.mysql-jpa.main.password=application"
    }
)
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("사용자를 등록할 때,")
    @Nested
    class RegisterUser {

        @DisplayName("사용자 정보가 유효하면, DB에 저장하고 사용자를 반환한다.")
        @Test
        void registersUser_whenUserInfoIsValid() {
            // arrange
            String userId = "user1";

            // act
            UserModel result = userService.registerUser(
                    userId,
                    "Password1!",
                    "홍길동",
                    LocalDate.of(1990, 1, 1),
                    "user1@example.com"
            );

            // assert
            Optional<UserModel> savedUser = userJpaRepository.findByUserId(userId);
            assertAll(
                    () -> assertThat(result.getUserId()).isEqualTo(userId),
                    () -> assertThat(result.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1)),
                    () -> assertThat(result.getEmail()).isEqualTo("user1@example.com"),
                    () -> assertThat(savedUser).isPresent(),
                    () -> assertThat(savedUser.map(UserModel::getUserId)).contains(userId),
                    () -> assertThat(savedUser.map(UserModel::getEmail)).contains("user1@example.com")
            );
        }
    }

    @DisplayName("사용자를 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("존재하는 로그인 ID와 비밀번호를 주면, 해당 사용자를 반환한다.")
        @Test
        void returnsUser_whenUserIdAndPasswordAreValid() {
            // arrange
            UserModel user = userJpaRepository.save(createUser());

            // act
            UserModel result = userService.getUser("user1", "Password1!");

            // assert
            assertAll(
                    () -> assertThat(result.getUserId()).isEqualTo(user.getUserId()),
                    () -> assertThat(result.getBirthDate()).isEqualTo(user.getBirthDate()),
                    () -> assertThat(result.getEmail()).isEqualTo(user.getEmail())
            );
        }

        @DisplayName("존재하지 않는 로그인 ID를 주면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsUserNotFound_whenUserIdDoesNotExist() {
            // arrange
            String userId = "unknown";

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getUser(userId, "Password1!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면, PASSWORD_MISMATCH 예외가 발생한다.")
        @Test
        void throwsPasswordMismatch_whenPasswordDoesNotMatch() {
            // arrange
            userJpaRepository.save(createUser());

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
            userJpaRepository.save(createUser());

            // act
            UserModel result = userService.changePassword("user1", "Password1!", "Password2!");

            // assert
            assertAll(
                    () -> assertThat(result.getUserId()).isEqualTo("user1"),
                    () -> assertDoesNotThrow(() -> userService.getUser("user1", "Password2!")),
                    () -> {
                        CoreException exception = assertThrows(CoreException.class, () -> {
                            userService.getUser("user1", "Password1!");
                        });
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
                    }
            );
        }

        @DisplayName("존재하지 않는 로그인 ID를 주면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsUserNotFound_whenUserIdDoesNotExist() {
            // arrange
            String userId = "unknown";

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword(userId, "Password1!", "Password2!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, PASSWORD_MISMATCH 예외가 발생한다.")
        @Test
        void throwsPasswordMismatch_whenCurrentPasswordDoesNotMatch() {
            // arrange
            userJpaRepository.save(createUser());

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1", "Wrong1!", "Password2!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
        }
    }

    private static UserModel createUser() {
        return new UserModel(
                "user1",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user1@example.com"
        );
    }
}
