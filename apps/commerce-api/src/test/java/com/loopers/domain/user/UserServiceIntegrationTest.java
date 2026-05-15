package com.loopers.domain.user;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보가 주어지면, 유저가 저장된다.")
        @Test
        void savesUser_whenValidUserInfoIsProvided() {
            // arrange
            UserModel user = new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            );

            // act
            UserModel saved = userService.signUp(user);

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isNotNull(),
                () -> assertThat(saved.getLoginId()).isEqualTo("user01"),
                () -> assertThat(saved.getName()).isEqualTo("홍길동"),
                () -> assertThat(saved.getEmail()).isEqualTo("user@example.com")
            );
        }

        @DisplayName("이미 존재하는 loginId로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdAlreadyExists() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            UserModel duplicateUser = new UserModel(
                "user01", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "other@example.com"
            );

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.signUp(duplicateUser)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("유효한 loginId와 password가 주어지면, 유저 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenValidCredentialsAreProvided() {
            // arrange
            UserModel saved = userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act
            UserModel result = userService.getUser("user01", "Password1!");

            // assert
            assertAll(
                () -> assertThat(result.getLoginId()).isEqualTo(saved.getLoginId()),
                () -> assertThat(result.getName()).isEqualTo(saved.getName()),
                () -> assertThat(result.getBirthDate()).isEqualTo(saved.getBirthDate()),
                () -> assertThat(result.getEmail()).isEqualTo(saved.getEmail())
            );
        }

        @DisplayName("존재하지 않는 loginId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdNotFound() {
            // arrange
            String notExistLoginId = "unknown";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.getUser(notExistLoginId, "Password1!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordNotMatches() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.getUser("user01", "WrongPassword!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class UpdatePassword {

        @DisplayName("기존 비밀번호가 일치하면, 비밀번호가 변경된다.")
        @Test
        void updatesPassword_whenOldPasswordMatches() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act
            userService.updatePassword("user01", "Password1!", "NewPassword1!");

            // assert
            UserModel updated = userService.getUser("user01", "NewPassword1!");
            assertThat(updated.matchesPassword("NewPassword1!")).isTrue();
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenOldPasswordNotMatches() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.updatePassword("user01", "WrongPassword!", "NewPassword1!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.updatePassword("user01", "Password1!", "Password1!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
