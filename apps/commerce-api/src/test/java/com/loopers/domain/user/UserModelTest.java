package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private static final String VALID_LOGIN_ID = "tester01";
    private static final String VALID_PASSWORD = "Password1!";
    private static final String VALID_NAME = "홍길동";
    private static final String VALID_BIRTH_DATE = "1990-05-14";
    private static final String VALID_EMAIL = "test@example.com";
    private static final Gender VALID_GENDER = Gender.M;

    @DisplayName("User 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 정보가 유효하면, User 가 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllInputsAreValid() {
            // act
            UserModel user = new UserModel(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER
            );

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(user.getPassword()).isEqualTo(VALID_PASSWORD),
                () -> assertThat(user.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(user.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL),
                () -> assertThat(user.getGender()).isEqualTo(VALID_GENDER)
            );
        }

        @DisplayName("로그인 ID 가 영문/숫자 10자 이내가 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"abcd1234567", "tester!", "테스터01", "with space"})
        void throwsBadRequest_whenLoginIdFormatIsInvalid(String invalidLoginId) {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> new UserModel(
                invalidLoginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER
            ));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 xx@yy.zz 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"plainaddress", "no-at-sign.com", "@missing-local.com", "no-domain@", "no-tld@example"})
        void throwsBadRequest_whenEmailFormatIsInvalid(String invalidEmail) {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> new UserModel(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, invalidEmail, VALID_GENDER
            ));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"1990/05/14", "90-05-14", "1990-5-14", "1990-13-01", "not-a-date"})
        void throwsBadRequest_whenBirthDateFormatIsInvalid(String invalidBirthDate) {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> new UserModel(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, invalidBirthDate, VALID_EMAIL, VALID_GENDER
            ));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8~16자 영문/숫자/특수문자 조합 규칙에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "Aa1!",
            "Aa1!Aa1!Aa1!Aa1!Aa1!",
            "abcdefgh",
            "12345678",
            "abc12345",
            "abcdefg!",
            "abcdefg1!한"
        })
        void throwsBadRequest_whenPasswordFormatIsInvalid(String invalidPassword) {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> new UserModel(
                VALID_LOGIN_ID, invalidPassword, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER
            ));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd) 이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            String birthDate = "1990-05-14";
            String passwordWithBirthDate = "Aa1!19900514";

            // act
            CoreException ex = assertThrows(CoreException.class, () -> new UserModel(
                VALID_LOGIN_ID, passwordWithBirthDate, VALID_NAME, birthDate, VALID_EMAIL, VALID_GENDER
            ));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름을 마스킹할 때, ")
    @Nested
    class GetMaskedName {

        @DisplayName("두 글자 이상이면, 마지막 글자가 * 로 마스킹된다.")
        @Test
        void masksLastChar_whenNameHasTwoOrMoreChars() {
            // arrange
            UserModel user = new UserModel(
                VALID_LOGIN_ID, VALID_PASSWORD, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER
            );

            // act + assert
            assertThat(user.getMaskedName()).isEqualTo("홍길*");
        }

        @DisplayName("한 글자이면, * 한 글자로 마스킹된다.")
        @Test
        void masksSingleChar_whenNameHasOneChar() {
            // arrange
            UserModel user = new UserModel(
                VALID_LOGIN_ID, VALID_PASSWORD, "박", VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER
            );

            // act + assert
            assertThat(user.getMaskedName()).isEqualTo("*");
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER);

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> user.changePassword("WrongPw1!", "NewPass2@"));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 형식 규칙(8~16자 영문/숫자/특수문자)에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Aa1!", "abcdefgh", "12345678", "abc12345", "abcdefg!"})
        void throwsBadRequest_whenNewPasswordFormatIsInvalid(String invalidNewPassword) {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER);

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> user.changePassword(VALID_PASSWORD, invalidNewPassword));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordEqualsCurrent() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER);

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> user.changePassword(VALID_PASSWORD, VALID_PASSWORD));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일(yyyyMMdd) 이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER);
            String newPasswordWithBirthDate = "Aa1!19900514";

            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> user.changePassword(VALID_PASSWORD, newPasswordWithBirthDate));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정상 입력이면, password 가 새 값으로 갱신된다.")
        @Test
        void changesPassword_whenInputIsValid() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER);
            String newPassword = "NewPass2@";

            // act
            user.changePassword(VALID_PASSWORD, newPassword);

            // assert
            assertThat(user.getPassword()).isEqualTo(newPassword);
        }
    }
}
