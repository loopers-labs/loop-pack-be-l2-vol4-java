package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("domain")
class UserModelTest {

    private static final String VALID_LOGIN_ID = "user123";
    private static final String VALID_PASSWORD = "Pass1234!";
    private static final String VALID_NAME = "홍길동";
    private static final String VALID_BIRTH_DATE = "19900101";
    private static final String VALID_EMAIL = "hong@example.com";

    @DisplayName("비밀번호 원문이 ")
    @Nested
    class Password {

        @DisplayName("8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsShorterThanEightCharacters() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, "Pass1!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsLongerThanSixteenCharacters() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, "Pass1234!Pass1234!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, "Pass" + VALID_BIRTH_DATE, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, "패스워드1234!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정확히 8자이면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenPasswordIsExactlyEightCharacters() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, "Pass123!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act & assert
            assertThat(new UserModel(command)).isNotNull();
        }

        @DisplayName("정확히 16자이면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenPasswordIsExactlySixteenCharacters() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, "Pass1234Pass123!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act & assert
            assertThat(new UserModel(command)).isNotNull();
        }
    }

    @DisplayName("이름이 ")
    @Nested
    class Name {

        @DisplayName("숫자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsDigit() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, "홍길1", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("특수문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsSpecialCharacter() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, "홍길@", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백을 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsSpace() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, "홍 길동", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 값이거나 공백만 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlankOrEmpty() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, "   ", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글이면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenNameIsKorean() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL);

            // act & assert
            assertThat(new UserModel(command)).isNotNull();
        }
    }

    @DisplayName("이메일이 ")
    @Nested
    class Email {

        @DisplayName("올바른 형식이면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenEmailIsValid() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "hong@example.com");

            // act & assert
            assertThat(new UserModel(command)).isNotNull();
        }

        @DisplayName("@가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailHasNoAtSign() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "hongexample.com");

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("도메인이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailHasNoDomain() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "hong@");

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로컬 파트가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailHasNoLocalPart() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "@example.com");

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsEmpty() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "");

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("생년월일이 ")
    @Nested
    class BirthDate {

        @DisplayName("yyyyMMdd 형식이면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenBirthDateIsValidFormat() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, "19900101", VALID_EMAIL);

            // act & assert
            assertThat(new UserModel(command)).isNotNull();
        }

        @DisplayName("1999-01-01 형식이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateHasHyphens() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, "1999-01-01", VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("월이 13 이상이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateHasInvalidMonth() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, "19991301", VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 날짜이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNonExistentDate() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, "19990230", VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("숫자 8자리가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNotEightDigits() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, "1990010", VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsEmpty() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, "", VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("로그인 ID가 ")
    @Nested
    class LoginId {

        @DisplayName("영문과 숫자로만 구성되면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenLoginIdContainsOnlyAlphanumeric() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand("user123", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act & assert
            assertThat(new UserModel(command)).isNotNull();
        }

        @DisplayName("한글을 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsKorean() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand("홍길동123", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("특수문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialCharacter() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand("user123!", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백을 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpace() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand("user 123", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsEmpty() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand("", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 회원 정보로 생성할 수 있다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            UserModel user = new UserModel(command);

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(user.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(user.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("잘못된 로그인 ID로 생성하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsInvalid() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand("홍길동!", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호 정책을 만족하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordDoesNotMeetPolicy() {
            // arrange
            UserRegistrationCommand command = new UserRegistrationCommand(VALID_LOGIN_ID, "short", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new UserModel(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 정책을 만족하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordDoesNotMeetPolicy() {
            // arrange
            PasswordEncoder encoder = new PasswordEncoder() {
                @Override
                public String encode(CharSequence raw) { return "encoded:" + raw; }
                @Override
                public boolean matches(CharSequence raw, String encoded) { return encoded.equals("encoded:" + raw); }
            };
            UserModel user = new UserModel(new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
            user.encodePassword(encoder);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> user.changePassword(VALID_PASSWORD, "short", encoder));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호 수정 시,")
    @Nested
    class ChangePassword {

        private final PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) { return "encoded:" + raw; }
            @Override
            public boolean matches(CharSequence raw, String encoded) { return encoded.equals("encoded:" + raw); }
        };

        private UserModel registeredUser() {
            UserModel user = new UserModel(new UserRegistrationCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
            user.encodePassword(encoder);
            return user;
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // arrange
            UserModel user = registeredUser();
            String newPassword = "Pass" + VALID_BIRTH_DATE;

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> user.changePassword(VALID_PASSWORD, newPassword, encoder));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
