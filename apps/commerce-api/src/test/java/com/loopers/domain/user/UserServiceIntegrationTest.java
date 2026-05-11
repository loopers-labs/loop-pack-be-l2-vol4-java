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

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 1);
    private static final String RAW_PASSWORD = "Password1!";
    private static final String NEW_PASSWORD = "Password2!";

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("사용자를 등록할 때,")
    @Nested
    class RegisterUser {

        @DisplayName("사용자 정보가 유효하면, DB에 사용자를 저장한다.")
        @Test
        void registersUser_whenUserInfoIsValid() {
            // arrange
            String userId = "user1";

            // act
            UserModel result = userService.registerUser(
                userId,
                RAW_PASSWORD,
                "홍길동",
                BIRTH_DATE,
                "user1@example.com"
            );

            // assert
            UserModel savedUser = userJpaRepository.findByUserId(userId).orElseThrow();
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo(userId),
                () -> assertThat(savedUser.getUserId()).isEqualTo(userId),
                () -> assertThat(savedUser.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(savedUser.getEmail()).isEqualTo("user1@example.com")
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, USER_ALREADY_EXISTS 예외가 발생한다.")
        @Test
        void throwsAlreadyExists_whenLoginIdAlreadyExists() {
            // arrange
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", NEW_PASSWORD, "김루프", BIRTH_DATE, "user2@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS);
        }

        @DisplayName("로그인 ID 중복이 DB 제약에서 발생해도, USER_ALREADY_EXISTS 예외로 변환된다.")
        @Test
        void throwsAlreadyExists_whenDuplicateLoginIdViolatesDatabaseConstraint() {
            // arrange
            UserModel firstUser = new UserModel("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");
            UserModel secondUser = new UserModel("user1", NEW_PASSWORD, "김루프", BIRTH_DATE, "user2@example.com");
            userRepository.save(firstUser);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userRepository.save(secondUser);
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS);
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("로그인 ID와 비밀번호가 일치하면, 내 정보를 반환한다.")
        @Test
        void returnsMyInfo_whenLoginHeadersAreValid() {
            // arrange
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            UserModel result = userService.getUser("user1", RAW_PASSWORD);

            // assert
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo("user1"),
                () -> assertThat(result.getMaskedName()).isEqualTo("홍길*"),
                () -> assertThat(result.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(result.getEmail()).isEqualTo("user1@example.com")
            );
        }

        @DisplayName("비밀번호가 일치하지 않으면, PASSWORD_MISMATCH 예외가 발생한다.")
        @Test
        void throwsAuthenticationFailed_whenPasswordDoesNotMatch() {
            // arrange
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getUser("user1", "Wrong1!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면, 비밀번호를 변경한다.")
        @Test
        void changesPassword_whenCurrentPasswordAndNewPasswordAreValid() {
            // arrange
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            UserModel result = userService.changePassword("user1", RAW_PASSWORD, NEW_PASSWORD);

            // assert
            UserModel savedUser = userJpaRepository.findByUserId("user1").orElseThrow();
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo("user1"),
                () -> assertThat(savedUser).isNotNull(),
                () -> assertDoesNotThrow(() -> userService.getUser("user1", NEW_PASSWORD)),
                () -> {
                    CoreException exception = assertThrows(CoreException.class, () -> {
                        userService.getUser("user1", RAW_PASSWORD);
                    });
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
                }
            );
        }
    }
}
