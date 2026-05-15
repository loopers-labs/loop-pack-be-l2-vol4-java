package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class UserServiceIntegrationTest {

    private static final String VALID_LOGIN_ID = "user123";
    private static final String VALID_PASSWORD = "Pass1234!";
    private static final String VALID_NAME = "홍길동";
    private static final String VALID_BIRTH_DATE = "19900101";
    private static final String VALID_EMAIL = "hong@example.com";

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserRegistrationCommand validCommand() {
        return new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
    }

    @DisplayName("회원가입 시,")
    @Nested
    class Register {

        @DisplayName("유효한 정보가 주어지면, User가 DB에 저장된다.")
        @Test
        void savesUserToDb_whenValidCommandIsGiven() {
            // arrange
            UserRegistrationCommand command = validCommand();

            // act
            userService.register(command);

            // assert
            assertThat(userJpaRepository.findByLoginId(VALID_LOGIN_ID)).isPresent();
        }

        @DisplayName("저장된 비밀번호는 원문과 다르다.")
        @Test
        void savesEncodedPassword_whenRegistering() {
            // arrange
            UserRegistrationCommand command = validCommand();

            // act
            UserModel result = userService.register(command);

            // assert
            assertThat(passwordEncoder.matches(VALID_PASSWORD, result.getPassword())).isTrue();
        }

        @DisplayName("로그인 ID 포맷이 틀리면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdFormatIsInvalid() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(
                "invalid@id!", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // act
            CoreException exception = assertThrows(CoreException.class, () -> userService.register(command));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("저장된 로그인 ID, 이름, 생년월일, 이메일이 맞다.")
        @Test
        void savesCorrectFields_whenRegistering() {
            // arrange
            UserRegistrationCommand command = validCommand();

            // act
            userService.register(command);

            // assert
            UserModel saved = userJpaRepository.findByLoginId(VALID_LOGIN_ID).orElseThrow();
            assertAll(
                () -> assertThat(saved.getLoginId()).isEqualTo(command.loginId()),
                () -> assertThat(saved.getName()).isEqualTo(command.name()),
                () -> assertThat(saved.getBirthDate()).isEqualTo(command.birthDate()),
                () -> assertThat(saved.getEmail()).isEqualTo(command.email())
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userService.register(validCommand());

            // act
            CoreException exception = assertThrows(CoreException.class, () -> userService.register(validCommand()));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("잘못된 비밀번호로 회원가입하면 실패하고 DB에 저장되지 않는다.")
        @Test
        void doesNotSaveToDb_whenPasswordIsInvalid() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(
                VALID_LOGIN_ID, "short", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );

            // act
            assertThrows(CoreException.class, () -> userService.register(command));

            // assert
            assertThat(userJpaRepository.findByLoginId(VALID_LOGIN_ID)).isEmpty();
        }
    }

    @DisplayName("내 정보 조회 시,")
    @Nested
    class Authenticate {

        @DisplayName("존재하지 않는 로그인 ID로 조회하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdDoesNotExist() {
            // arrange
            // (no user registered)

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> userService.authenticate("nonexistent", VALID_PASSWORD));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordDoesNotMatch() {
            // arrange
            userService.register(validCommand());

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> userService.authenticate(VALID_LOGIN_ID, "WrongPass1!"));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("유효한 헤더로 API 요청하면, 이름 마지막 글자가 마스킹된 내 정보를 반환한다.")
        @Test
        void returnsMaskedName_whenValidHeadersAreProvided() throws Exception {
            // arrange
            userService.register(validCommand());

            // act & assert
            mockMvc.perform(get("/api/v1/users/me")
                    .header("X-Loopers-LoginId", VALID_LOGIN_ID)
                    .header("X-Loopers-LoginPw", VALID_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍길*"));
        }
    }

    @DisplayName("비밀번호 수정 시,")
    @Nested
    class ChangePassword {

        @DisplayName("기존 비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
            // arrange
            UserModel user = userService.register(validCommand());

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> userService.changePassword(user.getLoginId(),"WrongPass1!", "NewPass1!"));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호 수정 성공 시 DB에 새 비밀번호가 저장된다.")
        @Test
        void savesNewPassword_whenChangePasswordSucceeds() {
            // arrange
            UserModel user = userService.register(validCommand());
            String newPassword = "NewPass1!";

            // act
            userService.changePassword(user.getLoginId(),VALID_PASSWORD, newPassword);

            // assert
            UserModel updated = userJpaRepository.findByLoginId(VALID_LOGIN_ID).orElseThrow();
            assertThat(passwordEncoder.matches(newPassword, updated.getPassword())).isTrue();
        }

        @DisplayName("새 비밀번호와 현재 비밀번호가 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            UserModel user = userService.register(validCommand());

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> userService.changePassword(user.getLoginId(),VALID_PASSWORD, VALID_PASSWORD));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsTooShort() {
            // arrange
            UserModel user = userService.register(validCommand());

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> userService.changePassword(user.getLoginId(),VALID_PASSWORD, "Short1!"));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호 수정 실패 시, DB의 비밀번호는 변경되지 않는다.")
        @Test
        void doesNotUpdatePassword_whenChangePasswordFails() {
            // arrange
            UserModel user = userService.register(validCommand());

            // act
            assertThrows(CoreException.class,
                () -> userService.changePassword(user.getLoginId(),"WrongPass1!", "NewPass1!"));

            // assert
            UserModel saved = userJpaRepository.findByLoginId(VALID_LOGIN_ID).orElseThrow();
            assertThat(passwordEncoder.matches(VALID_PASSWORD, saved.getPassword())).isTrue();
        }
    }
}
