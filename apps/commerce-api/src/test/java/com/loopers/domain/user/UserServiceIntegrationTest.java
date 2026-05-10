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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
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

    private static final String VALID_LOGIN_ID = "user123";
    private static final String VALID_PASSWORD = "Password1!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String VALID_EMAIL = "test@example.com";

    @DisplayName("회원가입을 할 때,")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 가입하면, 가입된 회원 정보를 반환한다.")
        @Test
        void returnsUserModel_whenAllFieldsAreValid() {
            // act
            UserModel result = userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(result.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(result.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(result.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userJpaRepository.save(new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.register(VALID_LOGIN_ID, "OtherPass1!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetMe {

        @DisplayName("유효한 로그인 ID와 비밀번호로 조회하면, 회원 정보를 반환한다.")
        @Test
        void returnsUserModel_whenLoginIdAndPasswordMatch() {
            // arrange
            userJpaRepository.save(new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // act
            UserModel result = userService.getUser(VALID_LOGIN_ID, VALID_PASSWORD);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(result.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(result.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(result.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.getUser("nonexistent", VALID_PASSWORD)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordDoesNotMatch() {
            // arrange
            userJpaRepository.save(new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.getUser(VALID_LOGIN_ID, "WrongPass1!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 유효하면, 정상적으로 변경된다.")
        @Test
        void changesPassword_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            // arrange
            userJpaRepository.save(new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
            String newPassword = "NewPass2@";

            // act & assert
            assertDoesNotThrow(() ->
                userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, newPassword)
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 변경 요청하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword("nonexistent", VALID_PASSWORD, "NewPass2@")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // arrange
            userJpaRepository.save(new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword(VALID_LOGIN_ID, "WrongPass1!", "NewPass2@")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            userJpaRepository.save(new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, VALID_PASSWORD)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
