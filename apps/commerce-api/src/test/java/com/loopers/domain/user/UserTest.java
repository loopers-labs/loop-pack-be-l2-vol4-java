package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @DisplayName("유저를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 loginId와 인코딩된 비밀번호가 주어지면 생성된다.")
        @Test
        void createsUser_whenValid() {
            String encoded = passwordEncoder.encode("password123!");
            User user = new User("tester", encoded);
            assertThat(user.getLoginId()).isEqualTo("tester");
        }

        @DisplayName("loginId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new User(null, "encoded"));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId가 공백이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new User("   ", "encoded"));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 검증할 때,")
    @Nested
    class MatchesPassword {

        @DisplayName("올바른 비밀번호면 true를 반환한다.")
        @Test
        void returnsTrue_whenPasswordMatches() {
            String raw = "password123!";
            User user = new User("tester", passwordEncoder.encode(raw));
            assertThat(user.matchesPassword(raw, passwordEncoder)).isTrue();
        }

        @DisplayName("틀린 비밀번호면 false를 반환한다.")
        @Test
        void returnsFalse_whenPasswordNotMatches() {
            User user = new User("tester", passwordEncoder.encode("password123!"));
            assertThat(user.matchesPassword("wrong!", passwordEncoder)).isFalse();
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 다르면 변경된다.")
        @Test
        void changesPassword_whenValid() {
            String current = "current123!";
            String newPw = "newPass456!";
            User user = new User("tester", passwordEncoder.encode(current));

            user.changePassword(current, newPw, passwordEncoder);

            assertThat(user.matchesPassword(newPw, passwordEncoder)).isTrue();
            assertThat(user.matchesPassword(current, passwordEncoder)).isFalse();
        }

        @DisplayName("현재 비밀번호가 틀리면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordWrong() {
            User user = new User("tester", passwordEncoder.encode("current123!"));

            CoreException result = assertThrows(CoreException.class,
                () -> user.changePassword("wrong!", "newPass456!", passwordEncoder));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordSameAsCurrent() {
            String password = "same123!";
            User user = new User("tester", passwordEncoder.encode(password));

            CoreException result = assertThrows(CoreException.class,
                () -> user.changePassword(password, password, passwordEncoder));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
