package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private static final PasswordHasher FAKE_HASHER = new PasswordHasher() {
        @Override public String encode(String raw) { return raw; }
        @Override public boolean matches(String raw, String encoded) { return raw.equals(encoded); }
    };
    private static final String VALID_LOGIN_ID = "user01";
    private static final String VALID_PASSWORD = "Password1!";
    private static final String VALID_NAME = "홍길동";
    private static final String VALID_BIRTH_DATE = "1990-01-01";
    private static final String VALID_EMAIL = "user@example.com";
    private static final Gender VALID_GENDER = Gender.MALE;

    @DisplayName("유저 모델을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("모든 필드가 유효하면 UserModel 객체 생성에 성공한다")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // arrange & act
            UserModel userModel = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER);

            // assert
            assertAll(
                () -> assertThat(userModel.getId()).isNotNull(),
                () -> assertThat(userModel.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(userModel.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(userModel.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(userModel.getEmail()).isEqualTo(VALID_EMAIL),
                () -> assertThat(userModel.getGender()).isEqualTo(VALID_GENDER)
            );
        }

        @DisplayName("이름이 null 이거나 빈 문자열이면 BAD_REQUEST 예외가 발생한다")
        @NullAndEmptySource
        @ParameterizedTest
        void throwsBadRequestException_whenNameIsNullOrEmpty(String name) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, name, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID 가 null 이거나 빈 문자열이면 BAD_REQUEST 예외가 발생한다")
        @NullAndEmptySource
        @ParameterizedTest
        void throwsBadRequestException_whenLoginIdIsNullOrEmpty(String loginId) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(loginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID 가 영문/숫자 10자 이내 형식에 맞지 않으면 BAD_REQUEST 예외가 발생한다")
        @ValueSource(strings = {"useruseruser", "홍길동user", "user!01", "user 01"})
        @ParameterizedTest
        void throwsBadRequestException_whenLoginIdIsInvalid(String loginId) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(loginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 xx@yy.zz 형식에 맞지 않으면 BAD_REQUEST 예외가 발생한다")
        @NullAndEmptySource
        @ValueSource(strings = {"userexample.com", "user@", "@example.com", "user@example"})
        @ParameterizedTest
        void throwsBadRequestException_whenEmailIsInvalid(String email) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, email, VALID_GENDER, FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식에 맞지 않으면 BAD_REQUEST 예외가 발생한다")
        @NullAndEmptySource
        @ValueSource(strings = {"19900101", "1990/01/01", "90-01-01", "1990-1-1"})
        @ParameterizedTest
        void throwsBadRequestException_whenBirthDateIsInvalid(String birthDate) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, birthDate, VALID_EMAIL, VALID_GENDER, FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 RULE 을 위반하면 BAD_REQUEST 예외가 발생한다 (상세 검증은 PasswordTest)")
        @Test
        void throwsBadRequestException_whenPasswordViolatesRule() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, "Pw1!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되어 있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDate() {
            // arrange
            String birthDate = "1990-01-01";
            String password = "Pass19900101!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, password, VALID_NAME, birthDate, VALID_EMAIL, VALID_GENDER, FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {
        @DisplayName("기존 비밀번호와 일치하고 신규 비밀번호가 RULE 을 만족하면 비밀번호가 변경된다")
        @Test
        void changesPassword_whenOldPasswordMatchesAndNewPasswordIsValid() {
            // arrange
            UserModel userModel = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER);
            String newPassword = "NewPass99!";

            // act & assert
            userModel.changePassword(VALID_PASSWORD, newPassword, FAKE_HASHER);
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenOldPasswordDoesNotMatch() {
            // arrange
            UserModel userModel = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userModel.changePassword("WrongPass1!", "NewPass99!", FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("신규 비밀번호가 기존 비밀번호와 동일하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenNewPasswordIsSameAsOld() {
            // arrange
            UserModel userModel = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userModel.changePassword(VALID_PASSWORD, VALID_PASSWORD, FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("신규 비밀번호가 RULE 을 위반하면 BAD_REQUEST 예외가 발생한다 (상세 검증은 PasswordTest)")
        @Test
        void throwsBadRequestException_whenNewPasswordViolatesRule() {
            // arrange
            UserModel userModel = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userModel.changePassword(VALID_PASSWORD, "pw", FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("신규 비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenNewPasswordContainsBirthDate() {
            // arrange
            UserModel userModel = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL, VALID_GENDER, FAKE_HASHER);
            String newPassword = "Pass19900101!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userModel.changePassword(VALID_PASSWORD, newPassword, FAKE_HASHER)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

}
