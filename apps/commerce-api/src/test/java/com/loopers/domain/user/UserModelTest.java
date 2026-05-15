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

    @DisplayName("유저 모델을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllFieldsAreValid() {
            // arrange
            String loginId = "user01";
            String password = "Password1!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 1);
            String email = "user@example.com";

            // act
            UserModel user = new UserModel(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(loginId),
                () -> assertThat(user.getName()).isEqualTo(name),
                () -> assertThat(user.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(user.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("loginId가 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            // arrange
            String blankLoginId = "   ";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel(blankLoginId, "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            // arrange
            String invalidLoginId = "user@01";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel(invalidLoginId, "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password가 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsBlank() {
            // arrange
            String blankPassword = "   ";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel("user01", blankPassword, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            String shortPassword = "Pass1!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel("user01", shortPassword, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            String longPassword = "Password123456789!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel("user01", longPassword, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 영문/숫자/특수문자 외의 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // arrange
            String invalidPassword = "패스워드1234!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel("user01", invalidPassword, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            // birthDate: 1990-01-01 → "19900101" 이 비밀번호에 포함
            String passwordWithBirthDate = "Pass19900101!";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel("user01", passwordWithBirthDate, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            String blankName = "   ";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel("user01", "Password1!", blankName, LocalDate.of(1990, 1, 1), "user@example.com")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birthDate가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel("user01", "Password1!", "홍길동", null, "user@example.com")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsBlank() {
            // arrange
            String blankEmail = "   ";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), blankEmail)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange
            String invalidEmail = "not-an-email";

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), invalidEmail)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class UpdatePassword {

        @DisplayName("유효한 새 비밀번호가 주어지면, 정상적으로 변경된다.")
        @Test
        void updatesPassword_whenNewPasswordIsValid() {
            // arrange
            UserModel user = new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com");

            // act
            user.updatePassword("Password1!", "NewPassword1!");

            // assert
            assertThat(user.matchesPassword("NewPassword1!")).isTrue();
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOldPasswordNotMatches() {
            // arrange
            UserModel user = new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                user.updatePassword("WrongPassword!", "NewPassword1!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsBlank() {
            // arrange
            UserModel user = new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                user.updatePassword("Password1!", "   ")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsTooShort() {
            // arrange
            UserModel user = new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                user.updatePassword("Password1!", "Pass1!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsTooLong() {
            // arrange
            UserModel user = new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                user.updatePassword("Password1!", "Password123456789!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            UserModel user = new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                user.updatePassword("Password1!", "Pass19900101!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            UserModel user = new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                user.updatePassword("Password1!", "Password1!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름을 마스킹할 때,")
    @Nested
    class MaskName {

        @DisplayName("이름의 마지막 글자가 *로 마스킹되어 반환된다.")
        @Test
        void masksLastCharacterOfName() {
            // arrange
            UserModel user = new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            );

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertThat(maskedName).isEqualTo("홍길*");
        }
    }
}
