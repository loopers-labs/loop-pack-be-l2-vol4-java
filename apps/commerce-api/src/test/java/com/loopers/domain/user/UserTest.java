package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

  @DisplayName("회원가입")
  @Nested
  class Create {

    @DisplayName("필요 정보가 모두 유효하면 User 객체 생성에 성공한다.")
    @Test
    void createsUser_whenRequiredFieldsAreValid() {
      // arrange
      String loginId = "loopers01";
      String password = "Password1!";
      String name = "홍길동";
      String birthDate = "1995-05-15";
      String email = "loopers@example.com";

      // act
      var user = User.create(loginId, password, name, birthDate, email);

      // assert
      assertAll(
          () -> assertThat(user.getId()).isNotNull(),
          () -> assertThat(user.getLoginId()).isEqualTo(loginId),
          () -> assertThat(user.getPassword()).isEqualTo(password),
          () -> assertThat(user.getName()).isEqualTo(name),
          () -> assertThat(user.getBirthDate()).isEqualTo(LocalDate.parse(birthDate)),
          () -> assertThat(user.getEmail()).isEqualTo(email));
    }

    @DisplayName("ID가 영문 및 숫자 10자 이내 형식에 맞지 않으면 User 객체 생성에 실패한다.")
    @Test
    void throwsBadRequestException_whenLoginIdFormatIsInvalid() {
      // act
      CoreException exception =
          assertThrows(
              CoreException.class,
              () ->
                  User.create(
                      "loopers_01", "Password1!", "홍길동", "1995-05-15", "loopers@example.com"));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("ID가 10자를 초과하면 User 객체 생성에 실패한다.")
    @Test
    void throwsBadRequestException_whenLoginIdExceedsMaxLength() {
      // act
      CoreException exception =
          assertThrows(
              CoreException.class,
              () ->
                  User.create(
                      "loopers2026", "Password1!", "홍길동", "1995-05-15", "loopers@example.com"));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("이름이 포맷에 맞지 않으면 User 객체 생성에 실패한다.")
    @Test
    void throwsBadRequestException_whenNameFormatIsInvalid() {
      // act
      CoreException exception =
          assertThrows(
              CoreException.class,
              () ->
                  User.create(
                      "loopers01", "Password1!", "홍길동1", "1995-05-15", "loopers@example.com"));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

  }
}
