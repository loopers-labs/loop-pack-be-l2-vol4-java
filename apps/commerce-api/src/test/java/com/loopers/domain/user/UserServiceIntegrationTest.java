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
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("내 정보 조회를 할 때,")
    @Nested
    class GetMyInfo {

        @DisplayName("해당 ID의 회원이 존재할 경우, 회원 정보가 반환된다.")
        @Test
        void returnsUserInfo_whenUserExists() {
            // arrange
            UserModel user = new UserModel("user1", "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE);
            userService.signUp(user);

            // act
            UserModel result = userService.findByLoginId("user1").orElse(null);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo("user1"),
                () -> assertThat(result.getName()).isEqualTo("홍길동"),
                () -> assertThat(result.getEmail()).isEqualTo("test@example.com"),
                () -> assertThat(result.getBirthDate()).isNotNull()
            );
        }

        @DisplayName("해당 ID의 회원이 존재하지 않을 경우, null이 반환된다.")
        @Test
        void returnsNull_whenUserNotFound() {
            // act
            UserModel result = userService.findByLoginId("nonexistent").orElse(null);

            // assert
            assertThat(result).isNull();
        }
    }

    @DisplayName("회원 가입을 할 때,")
    @Nested
    class SignUp {

        @DisplayName("정상적인 요청이면, User 저장이 수행된다.")
        @Test
        void savesUser_whenSignUpIsRequested() {
            // arrange
            UserModel user = new UserModel("user1", "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE);

            // act
            userService.signUp(user);

            // assert
            verify(userRepository).save(any(UserModel.class));
        }

        @DisplayName("회원 가입 시, 비밀번호가 암호화되어 저장된다.")
        @Test
        void savesEncryptedPassword_whenSignUpIsRequested() {
            // arrange
            String rawPassword = "Pass123!";
            UserModel user = new UserModel("user1", rawPassword, "홍길동", "test@example.com", "2000-01-01", Gender.MALE);

            // act
            UserModel saved = userService.signUp(user);

            // assert
            assertThat(passwordEncoder.matches(rawPassword, saved.getPassword())).isTrue();
        }

        @DisplayName("이미 가입된 ID로 회원가입 시도 시, 실패한다.")
        @Test
        void throwsException_whenLoginIdAlreadyExists() {
            // arrange
            UserModel user = new UserModel("user1", "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE);
            userService.signUp(user);

            UserModel duplicate = new UserModel("user1", "Pass456@", "김길동", "other@example.com", "1990-05-15", Gender.FEMALE);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> userService.signUp(duplicate));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
