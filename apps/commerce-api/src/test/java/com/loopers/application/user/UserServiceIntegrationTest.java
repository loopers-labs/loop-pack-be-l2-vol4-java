package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입을 할 때,")
    @Nested
    class SignUp {

        @DisplayName("정상 회원가입 시 UserModel 의 모든 필드가 영속화된다")
        @Test
        void persistsAllFields_whenSignUpIsSuccessful() {
            // given
            String loginId = "user01";
            String loginPw = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "user@example.com";
            Gender gender = Gender.MALE;

            // when
            userService.create(loginId, loginPw, name, birthDate, email, gender);

            // then
            UserModel persisted = userRepository.findByLoginId(loginId).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getId()).isNotNull(),
                    () -> assertThat(persisted.getLoginId().value()).isEqualTo(loginId),
                    () -> assertThat(persisted.getName()).isEqualTo(name),
                    () -> assertThat(persisted.getBirthDate().value()).isEqualTo(birthDate),
                    () -> assertThat(persisted.getEmail().value()).isEqualTo(email),
                    () -> assertThat(persisted.getGender()).isEqualTo(gender),
                    () -> assertThat(persisted.matchesPassword(loginPw, passwordEncryptor)).isTrue()
            );
        }

        @DisplayName("이미 가입된 ID 로 회원가입 시도 시 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflictException_whenDuplicateLoginIdIsProvided() {
            // given
            String loginId = "user01";
            userRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.create(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("기존 비밀번호가 일치하고 신규 비밀번호가 유효하면 영속된 비밀번호가 변경된다")
        @Test
        void changesPersistedPassword_whenAuthenticationIsValid() {
            // given
            String loginId = "user01";
            String oldPassword = "Password1!";
            String newPassword = "NewPass99!";
            userRepository.save(new UserModel(loginId, oldPassword, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));

            // when
            userService.changePassword(loginId, oldPassword, oldPassword, newPassword);

            // then
            UserModel updated = userRepository.findByLoginId(loginId).orElseThrow();
            assertThat(updated.matchesPassword(newPassword, passwordEncryptor)).isTrue();
        }

        @DisplayName("해당 ID 의 회원이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFoundException_whenUserDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.changePassword("nonexistent", "Password1!", "Password1!", "NewPass99!")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("로그인 비밀번호 인증이 실패하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenLoginPwAuthenticationFails() {
            // given
            String loginId = "user01";
            userRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.changePassword(loginId, "WrongPass1!", "Password1!", "NewPass99!")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginPw 와 oldPassword 가 일치하지 않으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenLoginPwAndOldPasswordDoNotMatch() {
            // given
            String loginId = "user01";
            userRepository.save(new UserModel(loginId, "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.changePassword(loginId, "Password1!", "WrongPass1!", "NewPass99!")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
