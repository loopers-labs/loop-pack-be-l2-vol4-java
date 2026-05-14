package com.loopers.domain.user;

import com.loopers.fixture.UserFixture;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

        @DisplayName("name 이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(UserFixture.LOGIN_ID, UserFixture.PASSWORD, "", UserFixture.BIRTH, UserFixture.EMAIL)
            );

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

        @DisplayName("birth 가 존재하지 않는 날짜면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthIsInvalidDate() {
            // arrange — 형식은 맞지만 실제로 존재하지 않는 날짜 (2월 30일)
            String invalidBirth = "1990-02-30";

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

        @DisplayName("여러 글자 이름은 마지막 글자만 '*' 로 치환된다.")
        @Test
        void returnsMaskedName_whenNameHasMultipleChars() {
            // arrange — "홍길동" (3글자)
            UserModel user = new UserModel(UserFixture.LOGIN_ID, UserFixture.PASSWORD, "홍길동", UserFixture.BIRTH, UserFixture.EMAIL);

            // act & assert
            assertThat(user.getMaskedName()).isEqualTo("홍길*");
        }

        @DisplayName("한 글자 이름은 '*' 만 반환한다.")
        @Test
        void returnsAsterisk_whenNameIsSingleChar() {
            // arrange — 이름이 1글자인 경우
            UserModel user = new UserModel(UserFixture.LOGIN_ID, UserFixture.PASSWORD, "홍", UserFixture.BIRTH, UserFixture.EMAIL);

            // act & assert
            assertThat(user.getMaskedName()).isEqualTo("*");
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        private FakePasswordEncoder encoder;

        @BeforeEach
        void setUp() {
            encoder = new FakePasswordEncoder();
        }

        @DisplayName("유효한 새 비밀번호로 변경 시, 비밀번호가 암호화되어 갱신된다.")
        @Test
        void changesPassword_whenValid() {
            // arrange — 현실적인 상태: 이미 암호화된 비번
            UserModel user = UserFixture.createModel();
            user.encodePassword(encoder); // "encoded:Password@1"

            // act
            user.changePassword("NewPass@99", encoder);

            // assert — 새 비밀번호로 matches 통과
            assertThat(encoder.matches("NewPass@99", user.getPassword())).isTrue();
        }

        @DisplayName("새 비밀번호가 형식 RULE 을 위반하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordViolatesFormat() {
            UserModel user = UserFixture.createModel();
            String tooShort = "abc1!";  // 5자 — 8자 미만

            CoreException ex = assertThrows(CoreException.class, () ->
                user.changePassword(tooShort, encoder)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirth() {
            UserModel user = UserFixture.createModel();  // BIRTH = "1990-01-01"
            String passwordWithBirth = "ab1990-01-01";

            CoreException ex = assertThrows(CoreException.class, () ->
                user.changePassword(passwordWithBirth, encoder)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange — 먼저 암호화 적용
            UserModel user = UserFixture.createModel();
            user.encodePassword(encoder);  // "encoded:Password@1"

            // act — 동일한 평문으로 변경 시도
            CoreException ex = assertThrows(CoreException.class, () ->
                user.changePassword(UserFixture.PASSWORD, encoder)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
