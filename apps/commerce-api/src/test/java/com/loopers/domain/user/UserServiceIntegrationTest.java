package com.loopers.domain.user;

import com.loopers.domain.user.command.SignUpUserCommand;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.PlainPassword;
import com.loopers.domain.user.vo.UserName;
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
    UserServiceIntegrationTest(UserService userService, PasswordEncoder passwordEncoder, DatabaseCleanUp databaseCleanUp) {
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
            User saved = signUp(
                "loopers01",
                rawPassword,
                "김성호",
                LocalDate.of(1993, 11, 3),
                "loopers@example.com"
            );

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isNotNull(),
                () -> assertThat(saved.getLoginId().value()).isEqualTo("loopers01"),
                () -> assertThat(saved.getPassword().value()).isNotEqualTo(rawPassword),
                () -> assertThat(passwordEncoder.matches(rawPassword, saved.getPassword().value())).isTrue()
            );
        }

        @DisplayName("이미 존재하는 로그인 ID 로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenLoginIdAlreadyExists() {
            // arrange
            signUp(
                "loopers01",
                "Loopers!2026",
                "김성호",
                LocalDate.of(1993, 11, 3),
                "loopers@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                signUp(
                    "loopers01",
                    "Different!9999",
                    "김다른",
                    LocalDate.of(1991, 1, 1),
                    "other@example.com"
                );
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("현재/새 비밀번호가 유효하면, 새 비밀번호가 인코딩되어 저장된다.")
        @Test
        void savesEncodedNewPassword_whenInputsAreValid() {
            // arrange
            String currentPassword = "Loopers!2026";
            String newPassword = "NewLoopers!9999";
            User saved = signUp(
                "loopers01",
                currentPassword,
                "김성호",
                LocalDate.of(1993, 11, 3),
                "loopers@example.com"
            );

            // act
            userService.changePassword(
                saved.getId(),
                currentPassword,
                newPassword
            );

            // assert
            User reloaded = userService.getUser(saved.getId());
            assertAll(
                () -> assertThat(passwordEncoder.matches(newPassword, reloaded.getPassword().value())).isTrue(),
                () -> assertThat(passwordEncoder.matches(currentPassword, reloaded.getPassword().value())).isFalse()
            );
        }

        @DisplayName("현재 비밀번호가 저장된 값과 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorizedException_whenCurrentPasswordDoesNotMatch() {
            // arrange
            User saved = signUp(
                "loopers01",
                "Loopers!2026",
                "김성호",
                LocalDate.of(1993, 11, 3),
                "loopers@example.com"
            );
            String wrongCurrentPassword = "Wrong!9999";
            String newPassword = "NewLoopers!9999";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(
                    saved.getId(),
                    wrongCurrentPassword,
                    newPassword
                );
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 정책에 어긋나면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewPasswordViolatesPolicy() {
            // arrange
            String currentPassword = "Loopers!2026";
            User saved = signUp(
                "loopers01",
                currentPassword,
                "김성호",
                LocalDate.of(1993, 11, 3),
                "loopers@example.com"
            );
            String tooShortNewPassword = "Aa1!56";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(
                    saved.getId(),
                    currentPassword,
                    tooShortNewPassword
                );
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String currentPassword = "Loopers!2026";
            User saved = signUp(
                "loopers01",
                currentPassword,
                "김성호",
                LocalDate.of(1993, 11, 3),
                "loopers@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(
                    saved.getId(),
                    currentPassword,
                    currentPassword
                );
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("ID 로 회원을 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID 면, 해당 회원을 반환한다.")
        @Test
        void returnsUser_whenIdExists() {
            // arrange
            User saved = signUp(
                "loopers01",
                "Loopers!2026",
                "김성호",
                LocalDate.of(1993, 11, 3),
                "loopers@example.com"
            );

            // act
            User found = userService.getUser(saved.getId());

            // assert
            assertAll(
                () -> assertThat(found.getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.getLoginId().value()).isEqualTo("loopers01"),
                () -> assertThat(found.getName().value()).isEqualTo("김성호")
            );
        }

        @DisplayName("존재하지 않는 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> userService.getUser(999_999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    private User signUp(
        String loginId,
        String rawPassword,
        String name,
        LocalDate birthDate,
        String email
    ) {
        BirthDate wrappedBirthDate = BirthDate.of(birthDate);
        SignUpUserCommand signUpUserCommand = new SignUpUserCommand(
            LoginId.of(loginId),
            PlainPassword.of(rawPassword, wrappedBirthDate),
            UserName.of(name),
            wrappedBirthDate,
            Email.of(email)
        );
        return userService.signUp(signUpUserCommand);
    }
}
