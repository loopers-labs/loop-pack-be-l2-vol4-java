package com.loopers.domain.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceIntegrationTest {

    private static final String DEFAULT_USER_ID = "usertest123";
    private static final String DEFAULT_PASSWORD = "abc123!@#";
    private static final String DEFAULT_NAME = "홍길동";
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(1995, 6, 10);
    private static final String DEFAULT_EMAIL = "test@naver.com";

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void saveDefaultUser() {
        UserModel userModel = new UserModel(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);
        userJpaRepository.save(userModel);
    }

    @DisplayName("회원가입")
    @Nested
    class Signup {

        @DisplayName("정상적인 인자들이 들어오면, 회원이 생성된다.")
        @Test
        void createUser_whenValidArgumentsAreProvided() {
            // act
            UserModel userModel = userService.signup(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);

            // assert
            assertAll(
                () -> assertNotNull(userModel.getId()),
                () -> assertEquals(DEFAULT_USER_ID, userModel.getUserId()),
                () -> assertEquals(DEFAULT_NAME, userModel.getName()),
                () -> assertEquals(DEFAULT_BIRTH_DATE, userModel.getBirthDate()),
                () -> assertEquals(DEFAULT_EMAIL, userModel.getEmail()),
                () -> assertTrue(passwordEncoder.matches(DEFAULT_PASSWORD, userModel.getPassword()))
            );
        }

        @DisplayName("이미 존재하는 userId로 회원가입 시도하면, Conflict 예외가 발생한다.")
        @Test
        void createUser_whenUserIdIsAlreadyTaken() {
            // arrange
            saveDefaultUser();

            // act
            CoreException coreException = assertThrows(CoreException.class, () ->
                userService.signup(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)
            );

            // assert
            assertEquals(ErrorType.CONFLICT, coreException.getErrorType());
        }
    }

    @DisplayName("회원 정보 조회")
    @Nested
    class GetUser {

        @BeforeEach
        void setup() {
            saveDefaultUser();
        }

        @DisplayName("존재하는 userId로 조회하면, 회원 정보를 반환한다.")
        @Test
        void getUser_whenValidArgumentsAreProvided() {
            // act
            UserModel userModel = userService.getUser(DEFAULT_USER_ID, DEFAULT_PASSWORD);

            // assert
            assertAll(
                () -> assertNotNull(userModel.getId()),
                () -> assertEquals(DEFAULT_USER_ID, userModel.getUserId()),
                () -> assertEquals(DEFAULT_NAME, userModel.getName()),
                () -> assertEquals(DEFAULT_BIRTH_DATE, userModel.getBirthDate()),
                () -> assertEquals(DEFAULT_EMAIL, userModel.getEmail())
            );
        }

        @DisplayName("잘못된 비밀번호로 조회 시도하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void getUser_whenPasswordIsWrong() {
            // arrange
            String wrongPassword = "wrongpw123!@#";

            // act
            CoreException coreException = assertThrows(CoreException.class, () ->
                userService.getUser(DEFAULT_USER_ID, wrongPassword)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, coreException.getErrorType());
        }

        @DisplayName("존재하지 않는 userId로 조회 시, NOT_FOUND 예외가 발생한다.")
        @Test
        void getUser_whenUserNotFound() {
            // arrange
            String notExistId = "notExist123";

            // act
            CoreException coreException = assertThrows(CoreException.class, () ->
                userService.getUser(notExistId, DEFAULT_PASSWORD)
            );

            // assert
            assertEquals(ErrorType.NOT_FOUND, coreException.getErrorType());
        }
    }

    @DisplayName("비밀번호 수정")
    @Nested
    class ChangePassword {

        @BeforeEach
        void setup() {
            saveDefaultUser();
        }

        @DisplayName("올바른 현재 비밀번호와 새 비밀번호가 주어지면, 비밀번호가 변경된다.")
        @Test
        void changePassword_whenValidArgumentsAreProvided() {
            // arrange
            String newPassword = "newpassword!@#";

            // act
            UserModel userModel = userService.changePassword(DEFAULT_USER_ID, DEFAULT_PASSWORD, newPassword);

            // assert
            assertTrue(passwordEncoder.matches(newPassword, userModel.getPassword()));
        }

        @DisplayName("현재 비밀번호가 일치하지 않는 경우, BAD_REQUEST 예외가 발생한다.")
        @Test
        void changePassword_whenCurrentPasswordIsWrong() {
            // arrange
            String wrongCurrentPassword = "wrongcurrent!@#";
            String newPassword = "newpassword!@#";

            // act
            CoreException coreException = assertThrows(CoreException.class, () ->
                userService.changePassword(DEFAULT_USER_ID, wrongCurrentPassword, newPassword)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, coreException.getErrorType());
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같은 경우, BAD_REQUEST 예외가 발생한다.")
        @Test
        void changePassword_whenNewPasswordIsSameAsCurrent() {
            // act
            CoreException coreException = assertThrows(CoreException.class, () ->
                userService.changePassword(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_PASSWORD)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, coreException.getErrorType());
        }
    }
}
