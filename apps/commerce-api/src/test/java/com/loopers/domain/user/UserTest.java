package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserTest {

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 정보가 정상이면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllInfoIsValid() {
            // arrange
            LoginId loginId = new LoginId("loopers123");
            Name name = new Name("김민우");
            Birth birth = new Birth(LocalDate.of(1990, 1, 1));
            Email email = new Email("user@example.com");
            String encodedPassword = "encoded:Pass1234!";

            // act
            User user = new User(loginId, name, birth, email, encodedPassword);

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(loginId.value()),
                () -> assertThat(user.getName()).isEqualTo(name.value()),
                () -> assertThat(user.getBirth()).isEqualTo(birth.value()),
                () -> assertThat(user.getEmail()).isEqualTo(email.value()),
                () -> assertThat(user.getEncodedPassword()).isEqualTo(encodedPassword)
            );
        }

    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @Mock
        private PasswordEncoder passwordEncoder;

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 유효하면, encodedPassword 가 새 비밀번호의 인코딩 결과로 교체된다.")
        @Test
        void replacesEncodedPassword_whenChangeIsValid() {
            // arrange
            String currentPassword = "Curr3nt!";
            String newPassword = "N3wPass!";
            String oldEncoded = "encoded:old";
            String newEncoded = "encoded:new";
            User user = new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                oldEncoded
            );
            given(passwordEncoder.matches(currentPassword, oldEncoded)).willReturn(true);
            given(passwordEncoder.encode(newPassword)).willReturn(newEncoded);

            // act
            user.changePassword(currentPassword, newPassword, passwordEncoder);

            // assert
            assertThat(user.getEncodedPassword()).isEqualTo(newEncoded);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String password = "Same1234!";
            String oldEncoded = "encoded:old";
            User user = new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                oldEncoded
            );
            given(passwordEncoder.matches(password, oldEncoded)).willReturn(true);

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> user.changePassword(password, password, passwordEncoder)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일(yyyyMMdd) 이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirth() {
            // arrange
            String currentPassword = "Curr3nt!";
            String newPassword = "Aa1!19900101";
            String oldEncoded = "encoded:old";
            User user = new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                oldEncoded
            );
            given(passwordEncoder.matches(currentPassword, oldEncoded)).willReturn(true);

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> user.changePassword(currentPassword, newPassword, passwordEncoder)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 형식 위반(허용되지 않는 문자 포함)이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordViolatesFormat() {
            // arrange
            String currentPassword = "Curr3nt!";
            String newPassword = "한글포함Pass1!";
            String oldEncoded = "encoded:old";
            User user = new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                oldEncoded
            );
            given(passwordEncoder.matches(currentPassword, oldEncoded)).willReturn(true);

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> user.changePassword(currentPassword, newPassword, passwordEncoder)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
            // arrange
            String currentPassword = "Wr0ng!";
            String newPassword = "N3wPass!";
            String oldEncoded = "encoded:old";
            User user = new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                oldEncoded
            );
            given(passwordEncoder.matches(currentPassword, oldEncoded)).willReturn(false);

            // act
            CoreException result = assertThrows(
                CoreException.class,
                () -> user.changePassword(currentPassword, newPassword, passwordEncoder)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
