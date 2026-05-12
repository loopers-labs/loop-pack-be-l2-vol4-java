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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때")
    @Nested
    class CreateUser {

        @DisplayName("정상 입력이면, 입력정보 및 암호화된 비밀번호가 저장된다.")
        @Test
        void persistsUserAndEncodesPassword_whenValidInput() {
            // given
            String loginId = "minbo";
            String rawPassword = "Test1234!";

            // when
            UserModel saved = userService.createUser(
                    loginId, rawPassword, "민보", LocalDate.of(1991, 8, 21), "test@example.com"
            );

            // then
            assertAll(
                    () -> assertThat(saved.getId()).isNotNull(),
                    () -> assertThat(saved.getLoginId()).isEqualTo(loginId),
                    () -> assertThat(saved.getPassword()).isNotEqualTo(rawPassword),
                    () -> assertThat(passwordEncoder.matches(rawPassword, saved.getPassword())).isTrue(),
                    () -> assertThat(userRepository.existsByLoginId(loginId)).isTrue()
            );
        }

        @DisplayName("이미 사용 중인 loginId 면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdExists() {
            // given
            userService.createUser("dup", "Pass1234!", "민보", LocalDate.of(2000, 1, 1), "first@example.com");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.createUser("dup", "Other5678!", "다른사람", LocalDate.of(1990, 5, 5), "second@example.com")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // given
            String loginId = "minbo";
            String passwordContainingBirth = "P20000101!";
            LocalDate birthDate = LocalDate.of(2000, 1, 1);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    userService.createUser(loginId, passwordContainingBirth, "민보", birthDate, "min@example.com")
            );

            // then
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                    () -> assertThat(userRepository.existsByLoginId(loginId)).isFalse()
            );
        }
    }
}
