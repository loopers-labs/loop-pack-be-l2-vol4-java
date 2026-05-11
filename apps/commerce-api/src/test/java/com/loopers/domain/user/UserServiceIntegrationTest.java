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

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    UserServiceIntegrationTest(
        UserService userService,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입 시, ")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보로 가입하면, 비밀번호가 인코딩되어 저장된다.")
        @Test
        void savesUserWithEncodedPassword_whenValidInputIsProvided() {
            // arrange
            String rawPassword = "Loopers!2026";

            // act
            UserModel saved = userService.signUp(
                "loopers01", rawPassword, "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
            );

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isNotNull(),
                () -> assertThat(saved.getLoginId()).isEqualTo("loopers01"),
                () -> assertThat(saved.getPassword()).isNotEqualTo(rawPassword),
                () -> assertThat(passwordEncoder.matches(rawPassword, saved.getPassword())).isTrue()
            );
        }

        @DisplayName("이미 존재하는 로그인 ID 로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenLoginIdAlreadyExists() {
            // arrange
            userService.signUp("loopers01", "Loopers!2026", "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signUp("loopers01", "Different!9999", "김다른", LocalDate.of(1991, 1, 1), "other@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("ID 로 회원을 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID 면, 해당 회원을 반환한다.")
        @Test
        void returnsUser_whenIdExists() {
            // arrange
            UserModel saved = userService.signUp(
                "loopers01", "Loopers!2026", "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
            );

            // act
            UserModel found = userService.getById(saved.getId());

            // assert
            assertAll(
                () -> assertThat(found.getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.getLoginId()).isEqualTo("loopers01"),
                () -> assertThat(found.getName()).isEqualTo("김성호")
            );
        }

        @DisplayName("존재하지 않는 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> userService.getById(999_999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
