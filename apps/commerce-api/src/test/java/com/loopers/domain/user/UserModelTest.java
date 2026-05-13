package com.loopers.domain.user;

import com.loopers.fixture.UserFixture;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    @DisplayName("유저 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("loginId 가 영문/숫자 10자 이내 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdFormatIsInvalid() {
            // arrange — 잘못된 loginId 한 개만 두고, 나머지는 전부 VALID
            String invalidLoginId = "한글아이디";

            // act — UserModel 생성 시 CoreException 이 던져질 것을 기대
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(invalidLoginId, UserFixture.PASSWORD, UserFixture.NAME, UserFixture.BIRTH, UserFixture.EMAIL)
            );

            // assert — ErrorType 이 BAD_REQUEST 인지
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email 이 xx@yy.zz 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange — 잘못된 email 한 개만 두고, 나머지는 전부 VALID
            String invalidEmail = "invalid-email";

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(UserFixture.LOGIN_ID, UserFixture.PASSWORD, UserFixture.NAME, UserFixture.BIRTH, invalidEmail)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birth 가 yyyy-MM-dd 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthFormatIsInvalid() {
            // arrange — 잘못된 birth 한 개만 두고, 나머지는 전부 VALID (구분자가 다름)
            String invalidBirth = "1990/01/01";

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(UserFixture.LOGIN_ID, UserFixture.PASSWORD, UserFixture.NAME, invalidBirth, UserFixture.EMAIL)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password 가 8~16자 영문/숫자/특수문자 규칙에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordFormatIsInvalid() {
            // arrange — 잘못된 password 한 개만 두고, 나머지는 전부 VALID (구분자가 다름)
            String invalidPassword = "abc1!";

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                    new UserModel(UserFixture.LOGIN_ID, invalidPassword, UserFixture.NAME, UserFixture.BIRTH, UserFixture.EMAIL)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password 에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirth() {
            // arrange — password 가 birth("1990-01-01") 를 포함, 비밀번호 RULE 의 다른 조건(8~16자, 영문/숫자/특수문자)은 만족
            String passwordContainingBirth = "ab1990-01-01";   // 12자, 영문+숫자+하이픈 → 형식은 통과해버림

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                    new UserModel(UserFixture.LOGIN_ID, passwordContainingBirth, UserFixture.NAME, UserFixture.BIRTH, UserFixture.EMAIL)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름을 마스킹할 때,")
    @Nested
    class MaskedName {

        @DisplayName("마지막 글자가 '*' 로 치환된 이름을 반환한다.")
        @Test
        void returnsMaskedName_whenCalled() {
            // arrange
            UserModel user = UserFixture.createModel(); // NAME = "홍길동"

            // act & assert
            assertAll(
                () -> assertThat(user.getMaskedName()).isEqualTo("홍길*"),
                () -> assertThat(user.getMaskedName()).endsWith("*"),
                () -> assertThat(user.getMaskedName()).startsWith("홍길")
            );
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("새 비밀번호가 형식 RULE 을 위반하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordViolatesFormat() {
            // arrange
            UserModel user = UserFixture.createModel();
            FakePasswordEncoder encoder = new FakePasswordEncoder();
            String tooShort = "abc1!";  // 5자 — 8자 미만

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                user.changePassword(tooShort, encoder)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirth() {
            // arrange
            UserModel user = UserFixture.createModel();  // BIRTH = "1990-01-01"
            FakePasswordEncoder encoder = new FakePasswordEncoder();
            String passwordWithBirth = "ab1990-01-01";  // 형식은 통과, birth 포함

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                user.changePassword(passwordWithBirth, encoder)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange — 먼저 암호화 적용
            UserModel user = UserFixture.createModel();
            FakePasswordEncoder encoder = new FakePasswordEncoder();
            user.encodePassword(encoder);  // password → "encoded:Password@1"

            // act — 동일한 평문으로 변경 시도
            CoreException ex = assertThrows(CoreException.class, () ->
                user.changePassword(UserFixture.PASSWORD, encoder)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
