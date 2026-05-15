package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 1);

    @Autowired
    private UserService userService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입할 때,")
    @Nested
    class Register {

        @DisplayName("올바른 정보를 모두 입력하면 회원이 생성된다.")
        @Test
        void registersUser_whenAllFieldsAreValid() {
            // arrange
            UserRegisterCommand command = new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            );

            // act
            UserModel result = userService.register(command);

            // assert
            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo("user123"),
                () -> assertThat(result.getName()).isEqualTo("홍길동"),
                () -> assertThat(result.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(result.getEmail()).isEqualTo("user@example.com")
            );
        }

        @DisplayName("이미 존재하는 loginId 로 가입하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            UserRegisterCommand duplicate = new UserRegisterCommand(
                "user123", "Password2@", "김철수", LocalDate.of(1995, 2, 2), "other@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> userService.register(duplicate));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("비밀번호가 8자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            UserRegisterCommand command = new UserRegisterCommand(
                "user123", "Pass1!", "홍길동", BIRTH_DATE, "user@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> userService.register(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자 초과이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            UserRegisterCommand command = new UserRegisterCommand(
                "user123", "Password1!Password2!", "홍길동", BIRTH_DATE, "user@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> userService.register(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않는 문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Password1한글", "Password1 !", "Password1\t!"})
        void throwsBadRequest_whenPasswordContainsInvalidChars(String password) {
            // arrange
            UserRegisterCommand command = new UserRegisterCommand(
                "user123", password, "홍길동", BIRTH_DATE, "user@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> userService.register(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            UserRegisterCommand command = new UserRegisterCommand(
                "user123", "19900101Pw!", "홍길동", BIRTH_DATE, "user@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> userService.register(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 BCrypt로 암호화되어 저장된다.")
        @Test
        void storesEncodedPassword_afterRegister() {
            // arrange
            String rawPassword = "Password1!";
            UserRegisterCommand command = new UserRegisterCommand(
                "user123", rawPassword, "홍길동", BIRTH_DATE, "user@example.com"
            );

            // act
            UserModel result = userService.register(command);

            // assert
            assertThat(result.getPassword()).isNotEqualTo(rawPassword);
            assertThat(result.getPassword()).startsWith("$2a$");
        }
    }

    @DisplayName("회원을 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("존재하는 loginId 로 조회하면 회원 정보를 반환한다.")
        @Test
        void returnsUser_whenLoginIdExists() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            UserModel result = userService.getUser("user123");

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo("user123")
            );
        }

        @DisplayName("존재하지 않는 loginId 로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userService.getUser("unknown"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("로그인 인증할 때,")
    @Nested
    class Authenticate {

        @DisplayName("올바른 loginId와 password를 입력하면 회원 정보를 반환한다.")
        @Test
        void returnsUser_whenCredentialsAreValid() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            UserModel result = userService.authenticate("user123", "Password1!");

            // assert
            assertThat(result.getLoginId()).isEqualTo("user123");
        }

        @DisplayName("비밀번호가 틀리면 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userService.authenticate("user123", "WrongPass1!"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 loginId로 인증하면 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userService.authenticate("unknown", "Password1!"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 유효하면 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenCurrentPasswordIsCorrectAndNewPasswordIsValid() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            userService.changePassword("user123", "Password1!", "NewPass2@");

            // assert: 새 비밀번호로 인증 가능
            UserModel result = userService.authenticate("user123", "NewPass2@");
            assertThat(result.getLoginId()).isEqualTo("user123");
        }

        @DisplayName("현재 비밀번호가 틀리면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordIsWrong() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userService.changePassword("user123", "WrongPass1!", "NewPass2@"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호 형식이 올바르지 않으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsInvalidFormat() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userService.changePassword("user123", "Password1!", "short"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userService.changePassword("user123", "Password1!", "Password1!"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            userService.register(new UserRegisterCommand(
                "user123", "Password1!", "홍길동", BIRTH_DATE, "user@example.com"
            ));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userService.changePassword("user123", "Password1!", "19900101Pw!"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
