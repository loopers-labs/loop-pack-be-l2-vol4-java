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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UpdateUserServiceIntegrationTest {

    @Autowired
    private UpdateUserService updateUserService;

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
            updateUserService.changePassword(loginId, oldPassword, oldPassword, newPassword);

            // then
            UserModel updated = userRepository.findByLoginId(loginId).orElseThrow();
            assertThat(updated.matchesPassword(newPassword, passwordEncryptor)).isTrue();
        }

        @DisplayName("해당 ID 의 회원이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFoundException_whenUserDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    updateUserService.changePassword("nonexistent", "Password1!", "Password1!", "NewPass99!")
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
                    updateUserService.changePassword(loginId, "WrongPass1!", "Password1!", "NewPass99!")
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
                    updateUserService.changePassword(loginId, "Password1!", "WrongPass1!", "NewPass99!")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
