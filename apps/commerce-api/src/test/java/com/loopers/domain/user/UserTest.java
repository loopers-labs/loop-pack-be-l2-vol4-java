package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        @DisplayName("loginId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new User(
                null,
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                "encoded"
            ));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> new User(
                new LoginId("loopers123"),
                null,
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                "encoded"
            ));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birth 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                null,
                new Email("user@example.com"),
                "encoded"
            ));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                null,
                "encoded"
            ));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("encodedPassword 가 null 이거나 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEncodedPasswordIsBlank() {
            CoreException result = assertThrows(CoreException.class, () -> new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                "  "
            ));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("새 인코딩된 비밀번호로 교체된다.")
        @Test
        void changesPassword_whenNewEncodedProvided() {
            // arrange
            User user = new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                "encoded:old"
            );
            String newEncoded = "encoded:new";

            // act
            user.changePassword(newEncoded);

            // assert
            assertThat(user.getEncodedPassword()).isEqualTo(newEncoded);
        }

        @DisplayName("새 인코딩된 비밀번호가 null 이거나 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewEncodedIsBlank() {
            // arrange
            User user = new User(
                new LoginId("loopers123"),
                new Name("김민우"),
                new Birth(LocalDate.of(1990, 1, 1)),
                new Email("user@example.com"),
                "encoded:old"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> user.changePassword("  "));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
