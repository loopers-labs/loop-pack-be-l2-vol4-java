package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


class MemberTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private Member createMember(String loginId, String name, String birthDate, String email) {
        Password password = Password.of("Password1!", birthDate, encoder.encode("Password1!"));
        return new Member(loginId, password, name, birthDate, email);
    }

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsMember_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "testUser1";

            // Act
            Member member = createMember(loginId, "홍길동", "1990-01-01", "test@example.com");

            // Assert
            assertThat(member.getLoginId()).isEqualTo(loginId);
        }

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsInvalid() {
            // Arrange
            String invalidEmail = "test.com";

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                createMember("testUser1", "홍길동", "1990-01-01", invalidEmail)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsInvalid() {
            // Arrange
            String invalidBirthDate = "19900101";

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                createMember("testUser1", "홍길동", invalidBirthDate, "test@example.com")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 기존 비밀번호와 새 비밀번호가 주어지면, password 필드가 교체된다.")
        @Test
        void changesPassword_whenCredentialsAreValid() {
            // Arrange
            Member member = createMember("testUser1", "홍길동", "1990-01-01", "test@example.com");
            Password oldPassword = member.getPassword();

            // Act
            member.changePassword("Password1!", "NewPassword2@", encoder.encode("NewPassword2@"), encoder);

            // Assert
            assertThat(member.getPassword()).isNotSameAs(oldPassword);
        }

    }

    @DisplayName("이름을 마스킹할 때, ")
    @Nested
    class MaskName {

        @DisplayName("이름의 마지막 글자가 * 로 바뀐다.")
        @Test
        void masksLastCharacter() {
            // Arrange
            Member member = createMember("testUser1", "홍길동", "1990-01-01", "test@example.com");

            // Act
            String masked = member.getMaskedName();

            // Assert
            assertThat(masked).isEqualTo("홍길*");
        }
    }
}
