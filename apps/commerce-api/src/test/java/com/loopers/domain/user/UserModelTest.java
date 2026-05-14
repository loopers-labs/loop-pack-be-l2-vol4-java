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

class UserModelTest {

    @DisplayName("UserModel 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성되고 각 필드가 그대로 보관된다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // given
            LoginId loginId = LoginId.of("loopers01");
            Password password = Password.of("Abcd1234!");
            String name = "김철수";
            BirthDate birthDate = BirthDate.of(LocalDate.of(1999, 3, 22));
            Email email = Email.of("user@example.com");

            // when
            UserModel userModel = UserModel.create(loginId, password, name, birthDate, email);

            // then
            assertAll(
                () -> assertThat(userModel.getLoginId()).isEqualTo(loginId),
                () -> assertThat(userModel.getPassword()).isEqualTo(password),
                () -> assertThat(userModel.getName()).isEqualTo(name),
                () -> assertThat(userModel.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(userModel.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("이름이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // given
            LoginId loginId = LoginId.of("loopers01");
            Password password = Password.of("Abcd1234!");
            String name = null;
            BirthDate birthDate = BirthDate.of(LocalDate.of(1999, 3, 22));
            Email email = Email.of("user@example.com");

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> UserModel.create(loginId, password, name, birthDate, email));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이름은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsEmpty() {
            // given
            LoginId loginId = LoginId.of("loopers01");
            Password password = Password.of("Abcd1234!");
            String name = "";
            BirthDate birthDate = BirthDate.of(LocalDate.of(1999, 3, 22));
            Email email = Email.of("user@example.com");

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> UserModel.create(loginId, password, name, birthDate, email));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이름은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("이름이 공백 문자로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsSpacesOnly() {
            // given
            LoginId loginId = LoginId.of("loopers01");
            Password password = Password.of("Abcd1234!");
            String name = "   ";
            BirthDate birthDate = BirthDate.of(LocalDate.of(1999, 3, 22));
            Email email = Email.of("user@example.com");

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> UserModel.create(loginId, password, name, birthDate, email));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이름은 비어있을 수 없습니다.")
            );
        }

    }
}
