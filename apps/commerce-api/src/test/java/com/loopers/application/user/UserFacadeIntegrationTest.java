package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.IntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@IntegrationTest
class UserFacadeIntegrationTest {

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입 시")
    @Nested
    class SignUp {

        @DisplayName("정상 입력이면 유저가 DB에 저장되고 비밀번호는 BCrypt로 암호화된다")
        @Test
        void persistsUserWithEncodedPassword_whenInputIsValid() {
            // given
            String loginId = "loopers01";
            String rawPassword = "Pass1234!";

            // when
            userFacade.signUp(loginId, rawPassword, "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");

            // then
            UserModel saved = userJpaRepository.findByLoginId(loginId).orElseThrow();
            assertAll(
                () -> assertThat(saved.getLoginId().getValue()).isEqualTo(loginId),
                () -> assertThat(saved.getEncodedPassword()).isNotEqualTo(rawPassword),
                () -> assertThat(passwordEncoder.matches(rawPassword, saved.getEncodedPassword())).isTrue()
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // given
            userFacade.signUp("loopers01", "Pass1234!", "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userFacade.signUp("loopers01", "Other9876@", "김철수", LocalDate.of(1990, 1, 1), "other@loopers.com")
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("인증 시")
    @Nested
    class Authenticate {

        @DisplayName("올바른 자격증명이면 userId를 반환한다")
        @Test
        void returnsUserId_whenCredentialsAreCorrect() {
            // given
            String loginId = "loopers01";
            String rawPassword = "Pass1234!";
            userFacade.signUp(loginId, rawPassword, "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");
            Long expectedId = userJpaRepository.findByLoginId(loginId).orElseThrow().getId();

            // when
            Long userId = userFacade.authenticate(loginId, rawPassword);

            // then
            assertThat(userId).isEqualTo(expectedId);
        }

        @DisplayName("틀린 비밀번호로 인증하면 UNAUTHORIZED 예외가 발생한다")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            // given
            String loginId = "loopers01";
            userFacade.signUp(loginId, "Pass1234!", "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userFacade.authenticate(loginId, "Wrong9876@")
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호 변경 시")
    @Nested
    class ChangePassword {

        @DisplayName("변경 후 새 비밀번호로는 인증되고 기존 비밀번호로는 UNAUTHORIZED 예외가 발생한다")
        @Test
        void newPasswordAuthenticates_andOldOneFails_afterChange() {
            // given
            String loginId = "loopers01";
            String oldPassword = "Pass1234!";
            String newPassword = "NewPw9876@";
            userFacade.signUp(loginId, oldPassword, "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");
            Long userId = userJpaRepository.findByLoginId(loginId).orElseThrow().getId();

            // when
            userFacade.changePassword(userId, oldPassword, newPassword);

            // then
            assertAll(
                () -> assertDoesNotThrow(() -> userFacade.authenticate(loginId, newPassword)),
                () -> {
                    CoreException ex = assertThrows(CoreException.class, () -> userFacade.authenticate(loginId, oldPassword));
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                }
            );
        }
    }
}
