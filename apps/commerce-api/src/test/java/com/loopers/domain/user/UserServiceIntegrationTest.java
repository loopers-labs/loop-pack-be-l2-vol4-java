package com.loopers.domain.user;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    private static final String LOGIN_ID = "user1234";
    private static final String PASSWORD = "abc123!?";
    private static final LocalDate BIRTH = LocalDate.of(1990, 1, 15);

    private final UserService userService;
    private final UserJpaRepository userJpaRepository;
    private final PasswordHasher passwordHasher;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    UserServiceIntegrationTest(
        UserService userService,
        UserJpaRepository userJpaRepository,
        PasswordHasher passwordHasher,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.userService = userService;
        this.userJpaRepository = userJpaRepository;
        this.passwordHasher = passwordHasher;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입할 때, ")
    @Nested
    class Signup {
        @DisplayName("유효한 요청이면, 회원 정보와 비밀번호 해시가 DB에 저장된다.")
        @Test
        void savesUserWithHashedPassword_whenRequestIsValid() {
            // arrange

            // act
            userService.signup(LOGIN_ID, PASSWORD, "홍길동", BIRTH, "user@example.com");

            // assert
            UserModel savedUser = userJpaRepository.findByLoginId(LOGIN_ID).orElseThrow();
            assertAll(
                () -> assertThat(savedUser.getLoginId()).isEqualTo(LOGIN_ID),
                () -> assertThat(savedUser.getName()).isEqualTo("홍길동"),
                () -> assertThat(savedUser.getBirth()).isEqualTo(BIRTH),
                () -> assertThat(savedUser.getEmail()).isEqualTo("user@example.com"),
                () -> assertThat(savedUser.getPasswordHash()).isNotEqualTo(PASSWORD),
                () -> assertThat(passwordHasher.matches(PASSWORD, savedUser.getPasswordHash())).isTrue()
            );
        }

        @DisplayName("비밀번호 정책을 통과하지 못하면, DB에 저장하지 않는다.")
        @Test
        void doesNotSaveUser_whenPasswordViolatesPolicy() {
            // arrange

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup(LOGIN_ID, "short", "홍길동", BIRTH, "user@example.com");
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(userJpaRepository.findByLoginId(LOGIN_ID)).isEmpty()
            );
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {
        @DisplayName("유효한 요청이면, 새 비밀번호 해시가 DB에 저장된다.")
        @Test
        void savesNewPasswordHash_whenRequestIsValid() {
            // arrange
            userService.signup(LOGIN_ID, PASSWORD, "홍길동", BIRTH, "user@example.com");

            // act
            userService.changePassword(LOGIN_ID, PASSWORD, "new123!?");

            // assert
            UserModel savedUser = userJpaRepository.findByLoginId(LOGIN_ID).orElseThrow();
            assertAll(
                () -> assertThat(passwordHasher.matches(PASSWORD, savedUser.getPasswordHash())).isFalse(),
                () -> assertThat(passwordHasher.matches("new123!?", savedUser.getPasswordHash())).isTrue()
            );
        }

        @DisplayName("현재 비밀번호가 틀리면, DB의 비밀번호를 변경하지 않는다.")
        @Test
        void doesNotChangePasswordHash_whenOldPasswordDoesNotMatch() {
            // arrange
            userService.signup(LOGIN_ID, PASSWORD, "홍길동", BIRTH, "user@example.com");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(LOGIN_ID, "wrong123!", "new123!?");
            });

            // assert
            UserModel savedUser = userJpaRepository.findByLoginId(LOGIN_ID).orElseThrow();
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
                () -> assertThat(passwordHasher.matches(PASSWORD, savedUser.getPasswordHash())).isTrue(),
                () -> assertThat(passwordHasher.matches("new123!?", savedUser.getPasswordHash())).isFalse()
            );
        }
    }
}
