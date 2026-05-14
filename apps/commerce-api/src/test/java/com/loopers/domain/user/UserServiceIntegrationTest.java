package com.loopers.domain.user;

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
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

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
    class Signup {

        @DisplayName("정상 입력이면 유저가 DB에 영속화되고 비밀번호는 BCrypt로 인코딩되어 저장된다")
        @Test
        void persistsUserWithBcryptEncodedPassword_whenInputIsValid() {
            // given
            String loginId = "loopers01";
            String rawPassword = "Pass1234!";

            // when
            UserModel returned = userService.signup(loginId, rawPassword, "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");

            // then
            UserModel saved = userJpaRepository.findByLoginId(new LoginId(loginId)).orElseThrow();
            assertAll(
                () -> assertThat(saved.getId()).isEqualTo(returned.getId()),
                () -> assertThat(saved.getLoginId().getValue()).isEqualTo(loginId),
                () -> assertThat(saved.getName().getValue()).isEqualTo("홍길동"),
                () -> assertThat(saved.getEmail().getValue()).isEqualTo("test@loopers.com"),
                () -> assertThat(saved.getEncodedPassword()).isNotEqualTo(rawPassword),
                () -> assertThat(passwordEncoder.matches(rawPassword, saved.getEncodedPassword())).isTrue()
            );
        }

        @DisplayName("이미 존재하는 loginId로 가입하면 CONFLICT 예외가 발생하고 추가 저장되지 않는다")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // given
            String loginId = "loopers01";
            userService.signup(loginId, "Pass1234!", "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");
            long beforeCount = userJpaRepository.count();

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.signup(loginId, "Other9876@", "김철수", LocalDate.of(1990, 1, 1), "other@loopers.com")
            );

            // then
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(userJpaRepository.count()).isEqualTo(beforeCount)
            );
        }

        @DisplayName("비밀번호에 생년월일 연도가 포함되면 BAD_REQUEST 예외가 발생하고 저장되지 않는다")
        @Test
        void throwsBadRequest_andDoesNotPersist_whenPasswordContainsBirthYear() {
            // given
            String loginId = "loopers01";
            String passwordWithBirthYear = "Aa!2002xy";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.signup(loginId, passwordWithBirthYear, "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com")
            );

            // then
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(userJpaRepository.findByLoginId(new LoginId(loginId))).isEmpty()
            );
        }
    }

    @DisplayName("인증 시")
    @Nested
    class Authenticate {

        @DisplayName("올바른 자격증명이면 실제 BCrypt 매칭을 통과해 유저를 반환한다")
        @Test
        void returnsUser_whenCredentialsMatchWithRealBcrypt() {
            // given
            String loginId = "loopers01";
            String rawPassword = "Pass1234!";
            userService.signup(loginId, rawPassword, "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");

            // when
            UserModel authenticated = userService.authenticate(loginId, rawPassword);

            // then
            assertThat(authenticated.getLoginId().getValue()).isEqualTo(loginId);
        }

        @DisplayName("비밀번호가 틀리면 UNAUTHORIZED 예외가 발생한다 (실제 BCrypt 불일치)")
        @Test
        void throwsUnauthorized_whenPasswordDoesNotMatchWithRealBcrypt() {
            // given
            String loginId = "loopers01";
            userService.signup(loginId, "Pass1234!", "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.authenticate(loginId, "Wrong9876@")
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("ID로 조회 시")
    @Nested
    class GetById {

        @DisplayName("영속된 유저는 ID로 조회된다")
        @Test
        void returnsPersistedUser_whenIdExists() {
            // given
            String loginId = "loopers01";
            userService.signup(loginId, "Pass1234!", "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");
            Long persistedId = userJpaRepository.findByLoginId(new LoginId(loginId)).orElseThrow().getId();

            // when
            UserModel found = userService.getById(persistedId);

            // then
            assertAll(
                () -> assertThat(found.getId()).isEqualTo(persistedId),
                () -> assertThat(found.getLoginId().getValue()).isEqualTo(loginId)
            );
        }
    }

    @DisplayName("비밀번호 변경 시")
    @Nested
    class ChangePassword {

        @DisplayName("변경하면 dirty checking으로 DB의 encodedPassword가 새 인코딩 값으로 반영된다")
        @Test
        void persistsNewEncodedPassword_viaDirtyChecking() {
            // given
            String loginId = "loopers01";
            String oldPassword = "Pass1234!";
            String newPassword = "NewPw9876@";
            userService.signup(loginId, oldPassword, "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");
            Long userId = userJpaRepository.findByLoginId(new LoginId(loginId)).orElseThrow().getId();
            String oldEncoded = userJpaRepository.findById(userId).orElseThrow().getEncodedPassword();

            // when
            userService.changePassword(userId, oldPassword, newPassword);

            // then
            String newEncoded = userJpaRepository.findById(userId).orElseThrow().getEncodedPassword();
            assertAll(
                () -> assertThat(newEncoded).isNotEqualTo(oldEncoded),
                () -> assertThat(passwordEncoder.matches(newPassword, newEncoded)).isTrue(),
                () -> assertThat(passwordEncoder.matches(oldPassword, newEncoded)).isFalse()
            );
        }

        @DisplayName("변경 후 새 비밀번호로는 인증되고 옛 비밀번호로는 UNAUTHORIZED 예외가 발생한다")
        @Test
        void newPasswordAuthenticates_andOldOneFails_afterChange() {
            // given
            String loginId = "loopers01";
            String oldPassword = "Pass1234!";
            String newPassword = "NewPw9876@";
            userService.signup(loginId, oldPassword, "홍길동", LocalDate.of(2002, 5, 11), "test@loopers.com");
            Long userId = userJpaRepository.findByLoginId(new LoginId(loginId)).orElseThrow().getId();

            // when
            userService.changePassword(userId, oldPassword, newPassword);

            // then
            assertAll(
                () -> assertDoesNotThrow(() -> userService.authenticate(loginId, newPassword)),
                () -> {
                    CoreException ex = assertThrows(CoreException.class, () -> userService.authenticate(loginId, oldPassword));
                    assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                }
            );
        }
    }
}
