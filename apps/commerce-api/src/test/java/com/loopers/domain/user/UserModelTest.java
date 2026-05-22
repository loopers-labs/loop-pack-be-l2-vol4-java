package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            assertThat(user)
                .satisfies(u -> assertThat(u.getLoginId()).isEqualTo(loginId))
                .satisfies(u -> assertThat(u.getName()).isEqualTo(name))
                .satisfies(u -> assertThat(u.getBirthDate()).isEqualTo(birthDate))
                .satisfies(u -> assertThat(u.getEmail()).isEqualTo(email));
        }

        @DisplayName("loginId가 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            // arrange
            String blankLoginId = "   ";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel(blankLoginId, "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            // arrange
            String invalidLoginId = "user@01";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel(invalidLoginId, "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password가 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsBlank() {
            // arrange
            String blankPassword = "   ";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", blankPassword, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            String shortPassword = "Pass1!";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", shortPassword, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            String longPassword = "Password123456789!";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", longPassword, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 영문/숫자/특수문자 외의 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // arrange
            String invalidPassword = "패스워드1234!";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", invalidPassword, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 생년월일 8자리(yyyyMMdd)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            String passwordWithBirthDate = "Pass19900101!";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", passwordWithBirthDate, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 생년월일 6자리(yyMMdd)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate_6digits() {
            // arrange
            // birthDate: 1990-01-01 → "900101"
            String passwordWithBirthDate = "Pass900101!!";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", passwordWithBirthDate, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 생년월일 4자리 월일(MMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate_4digits() {
            // arrange
            // birthDate: 1990-01-01 → "0101"
            String passwordWithBirthDate = "Pass0101Word!";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", passwordWithBirthDate, "홍길동", LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            String blankName = "   ";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", "Password1!", blankName, LocalDate.of(1990, 1, 1), "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birthDate가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", "Password1!", "홍길동", null, "user@example.com")
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsBlank() {
            // arrange
            String blankEmail = "   ";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), blankEmail)
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange
            String invalidEmail = "not-an-email";

            // act & assert
            assertThatThrownBy(() ->
                new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), invalidEmail)
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class UpdatePassword {

        @DisplayName("인코딩된 새 비밀번호가 주어지면, 정상적으로 변경된다.")
        @Test
        void updatesPassword_whenEncodedNewPasswordIsGiven() {
            // arrange
            UserModel user = new UserModel("user01", "Password1!", "홍길동", LocalDate.of(1990, 1, 1), "user@example.com");
            String encodedNew = "$2a$10$encodedHashValue1234567890abcdef";

            // act
            user.updatePassword(encodedNew);

            // assert
            assertThat(user.getPassword()).isEqualTo(encodedNew);
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
