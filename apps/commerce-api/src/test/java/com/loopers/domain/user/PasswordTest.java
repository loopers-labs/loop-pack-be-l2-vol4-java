package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordTest {

  @DisplayName("Password VO를 생성할 때,")
  @Nested
  class Create {

    @DisplayName("8~16자의 영문 대소문자, 숫자, 특수문자만 포함하면 Password 생성에 성공한다.")
    @Test
    void createsPassword_whenRuleIsValid() {
      // arrange
      String value = "Password1!";
      BirthDate birthDate = BirthDate.of("1995-05-15");

      // act
      Password password = Password.of(value, birthDate);

      // assert
      assertThat(password.getValue()).isEqualTo(value);
    }

    @DisplayName("비밀번호가 8자 미만이면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenPasswordIsTooShort() {
      // act
      CoreException exception =
          assertThrows(CoreException.class, () -> Password.of("Pw1!", BirthDate.of("1995-05-15")));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("비밀번호가 16자를 초과하면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenPasswordIsTooLong() {
      // act
      CoreException exception =
          assertThrows(
              CoreException.class,
              () -> Password.of("Password123456789!", BirthDate.of("1995-05-15")));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("비밀번호에 허용되지 않는 문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenPasswordContainsInvalidCharacter() {
      // act
      CoreException exception =
          assertThrows(
              CoreException.class,
              () -> Password.of("Password한글1!", BirthDate.of("1995-05-15")));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenPasswordContainsBirthDate() {
      // act
      CoreException basicDateException =
          assertThrows(
              CoreException.class,
              () -> Password.of("19950515!", BirthDate.of("1995-05-15")));
      CoreException isoDateException =
          assertThrows(
              CoreException.class,
              () -> Password.of("1995-05-15!", BirthDate.of("1995-05-15")));

      // assert
      assertAll(
          () -> assertThat(basicDateException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
          () -> assertThat(isoDateException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
  }
}
