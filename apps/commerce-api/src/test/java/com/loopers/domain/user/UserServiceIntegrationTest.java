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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입을 할 때, ")
    @Nested
    class SignUp {

        @DisplayName("정상 입력이면, User 저장이 수행되고 저장된 User 가 반환된다.")
        @Test
        void persistsUser_whenInputIsValid() {
            // arrange
            UserModel user = new UserModel(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", "M"
            );

            // act
            UserModel saved = userService.signUp(user);

            // assert
            assertAll(
                () -> verify(userRepository).save(any(UserModel.class)),
                () -> assertThat(saved.getId()).isPositive(),
                () -> assertThat(saved.getLoginId()).isEqualTo("tester01")
            );
        }

        @DisplayName("이미 가입된 로그인 ID 가 존재하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            UserModel existing = new UserModel(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", "M"
            );
            userService.signUp(existing);

            UserModel duplicate = new UserModel(
                "tester01", "Password2@", "김철수", "1991-06-15", "another@example.com", "F"
            );

            // act
            CoreException ex = assertThrows(CoreException.class, () -> userService.signUp(duplicate));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내 정보를 조회할 때, ")
    @Nested
    class GetMyInfo {

        @DisplayName("해당 로그인 ID 의 회원이 존재하면, 회원 정보가 반환된다.")
        @Test
        void returnsUser_whenLoginIdExists() {
            // arrange
            UserModel saved = userService.signUp(new UserModel(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", "M"
            ));

            // act
            Optional<UserModel> result = userService.getMyInfo("tester01");

            // assert
            assertAll(
                () -> assertThat(result).isPresent(),
                () -> assertThat(result.get().getId()).isEqualTo(saved.getId()),
                () -> assertThat(result.get().getLoginId()).isEqualTo("tester01"),
                () -> assertThat(result.get().getName()).isEqualTo("홍길동")
            );
        }

        @DisplayName("해당 로그인 ID 의 회원이 존재하지 않으면, Optional.empty 가 반환된다.")
        @Test
        void returnsEmpty_whenLoginIdNotFound() {
            // act
            Optional<UserModel> result = userService.getMyInfo("nonexistent");

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("정상 입력이면, 저장된 비밀번호가 새 값으로 갱신된다.")
        @Test
        void persistsNewPassword_whenInputIsValid() {
            // arrange
            userService.signUp(new UserModel(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", "M"
            ));

            // act
            userService.changePassword("tester01", "Password1!", "NewPass2@");

            // assert
            UserModel reloaded = userService.getMyInfo("tester01").orElseThrow();
            assertThat(reloaded.getPassword()).isEqualTo("NewPass2@");
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordMismatch() {
            // arrange
            userService.signUp(new UserModel(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", "M"
            ));

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> userService.changePassword("tester01", "WrongPw1!", "NewPass2@"));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
