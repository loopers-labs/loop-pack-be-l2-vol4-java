package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

  @DisplayName("회원 가입 단위 테스트")
  @Nested
  class SignupTest {

    @DisplayName("필요 정보가 모두 유효하면 User 객체 생성에 성공한다.")
    @Test
    void createsUser_whenRequiredFieldsAreValid() {
      // act
      var user = UserFixture.defaultUser();

      // assert
      assertAll(
          () -> assertThat(user.getId()).isNotNull(),
          () -> assertThat(user.getLoginId()).isEqualTo(UserFixture.LOGIN_ID),
          () -> assertThat(user.getPassword()).isEqualTo(UserFixture.PASSWORD),
          () -> assertThat(user.getName()).isEqualTo(UserFixture.NAME),
          () -> assertThat(user.getBirthDate()).isEqualTo(LocalDate.parse(UserFixture.BIRTH_DATE)),
          () -> assertThat(user.getEmail()).isEqualTo(UserFixture.EMAIL));
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
                      "loopers_01",
                      UserFixture.PASSWORD,
                      UserFixture.NAME,
                      UserFixture.BIRTH_DATE,
                      UserFixture.EMAIL));

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
                      "loopers2026",
                      UserFixture.PASSWORD,
                      UserFixture.NAME,
                      UserFixture.BIRTH_DATE,
                      UserFixture.EMAIL));

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
                      UserFixture.LOGIN_ID,
                      UserFixture.PASSWORD,
                      "홍길동1",
                      UserFixture.BIRTH_DATE,
                      UserFixture.EMAIL));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

  }

  @DisplayName("비밀번호 수정 단위 테스트")
  @Nested
  class ChangePasswordTest {

    @DisplayName("새 비밀번호가 비밀번호 규칙에 맞고 현재 비밀번호와 다르면 비밀번호가 변경된다.")
    @Test
    void changesPassword_whenNewPasswordIsValidAndDifferent() {
      // arrange
      var user = UserFixture.defaultUser();

      // act
      user.changePassword(UserFixture.PASSWORD, UserFixture.NEW_PASSWORD);

      // assert
      assertThat(user.getPassword()).isEqualTo(UserFixture.NEW_PASSWORD);
    }

    @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenNewPasswordEqualsCurrentPassword() {
      // arrange
      var user = UserFixture.defaultUser();

      // act
      CoreException exception =
          assertThrows(
              CoreException.class,
              () -> user.changePassword(UserFixture.PASSWORD, UserFixture.PASSWORD));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("새 비밀번호가 비밀번호 규칙에 맞지 않으면 BAD_REQUEST가 발생한다.")
    @Test
    void throwsBadRequestException_whenNewPasswordViolatesPasswordRule() {
      // arrange
      var user = UserFixture.defaultUser();

      // act
      CoreException exception =
          assertThrows(
              CoreException.class,
              () -> user.changePassword(UserFixture.PASSWORD, "19950515!"));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
  }
}
