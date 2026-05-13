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

    @DisplayName("회원가입")
    @Nested
    class Signup {

        // happy path 우선 작성
        @DisplayName("정상적인 인자들이 들어오면, 회원이 생성된다.")
        @Test
        void createUser_whenValidArgumentsAreProvided() {
            // arrange
            // 회원 정보 생성
            String userId = "usertest123";
            String name = "홍길동";
            String password = "abc123!@#";
            LocalDate birthDate = LocalDate.of(1995, 6, 10);
            String email = "test@naver.com";

            // act = then
            UserModel userModel = userService.signup(userId, password, name, birthDate, email);

            // assert
            // userModel이 정확히 생성되었는지 검증
            assertAll(
                    () -> assertNotNull(userModel.getId()),
                    () -> assertEquals(userId, userModel.getUserId()),
                    () -> assertEquals(name, userModel.getName()),
                    () -> assertEquals(birthDate, userModel.getBirthDate()),
                    () -> assertEquals(email, userModel.getEmail()),
                    () -> assertTrue(passwordEncoder.matches(password, userModel.getPassword()))
            );
        }

        @DisplayName("이미 존재하는 userId로 회원가입 시도하면, Conflict 예외가 발생한다.")
        @Test
        void createUser_whenUserIdIsAlreadyTaken() {
            // arrange
            // 회원 정보 생성
            String userId = "usertest123";
            String name = "홍길동";
            String password = "abc123!@#";
            LocalDate birthDate = LocalDate.of(1995, 6, 10);
            String email = "test@naver.com";

            UserModel userModel = new UserModel(userId, password, name, birthDate, email);
            userJpaRepository.save(userModel);

            // act
            CoreException coreException = assertThrows(CoreException.class, () -> {
                userService.signup(userId, password, name, birthDate, email);
            });

            // assert
            assertEquals(ErrorType.CONFLICT, coreException.getErrorType());
        }
    }

    @DisplayName("회원 정보 조회")
    @Nested
    class getUser {

        @BeforeEach
        void setup() {
            // 회원 정보 생성
            String userId = "usertest123";
            String name = "홍길동";
            String password = "abc123!@#";
            LocalDate birthDate = LocalDate.of(1995, 6, 10);
            String email = "test@naver.com";

            UserModel userModel = new UserModel(userId, password, name, birthDate, email);
            userJpaRepository.save(userModel);
        }

        // happy path 우선 작성
        @DisplayName("존재하는 id 확인. 회원 정보 return")
        @Test
        void getUser_whenValidArgumentsAreProvided() {
            // arrange
            // beforeEach로 항상 저장되어 있으므로 pass

            // act
            UserModel userModel = userService.getUser("usertest123", "abc123!@#");

            // assert
            assertAll(
                () -> assertNotNull(userModel.getId()),
                () -> assertEquals("usertest123", userModel.getUserId()),
                () -> assertEquals("홍길동", userModel.getName()),
                () -> assertEquals(LocalDate.of(1995, 6, 10), userModel.getBirthDate())
            );
        }

        @DisplayName("잘못된 비밀번호로 조회 시도하면, BAD_REQUEST 에러 발생")
        @Test
        void getUser_whenPasswordIsWrong() {
            //arrange
            String wrongPassword = "wrongpw123!@#";

            // act
            CoreException coreException = assertThrows(CoreException.class, () -> {
                userService.getUser("usertest123", wrongPassword);
            });

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
                    userService.getUser(notExistId, "abc123!@#")
            );

            // assert
            assertEquals(ErrorType.NOT_FOUND, coreException.getErrorType());
        }
    }

    @DisplayName("비밀번호 수정")
    @Nested
    class changePassword {
        // happy path 작성
        @BeforeEach
        void setup() {
            // 회원 정보 생성
            String userId = "usertest123";
            String name = "홍길동";
            String password = "abc123!@#";
            LocalDate birthDate = LocalDate.of(1995, 6, 10);
            String email = "test@naver.com";

            UserModel userModel = new UserModel(userId, password, name, birthDate, email);
            userJpaRepository.save(userModel);
        }

        @DisplayName("비밀번호 정상 수정 Case")
        @Test()
        void changePassword_whenValidArgumentsAreProvided() {
            // arrange
            String newPassword = "newpassword!@#";

            // act
            UserModel userModel = userService.changePassword("usertest123", "abc123!@#", newPassword);

            // assert
            assertTrue(passwordEncoder.matches(newPassword, userModel.getPassword()));
        }

        @DisplayName("현재 비밀번호가 일치하지 않는 경우, BAD_REQUEST 발생")
        @Test()
        void changePassword_whenCurrentPasswordIsWrong() {
            // arrange
            String wrongCurrentPassword = "wrongcurrent!@#";
            String newPassword = "newpassword!@#";

            // act
            CoreException coreException = assertThrows(CoreException.class, () -> {
                userService.changePassword("usertest123", wrongCurrentPassword, newPassword);
            });

            // assert
            assertEquals(ErrorType.BAD_REQUEST, coreException.getErrorType());
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같은 경우, BAD_REQUEST 발생")
        @Test
        void changePassword_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String currentPassword = "abc123!@#"; // 현재 비밀번호와 동일

            // act
            CoreException coreException = assertThrows(CoreException.class, () -> {
                userService.changePassword("usertest123", currentPassword, currentPassword);
            });

            // assert
            assertEquals(ErrorType.BAD_REQUEST, coreException.getErrorType());
        }
    }
}
