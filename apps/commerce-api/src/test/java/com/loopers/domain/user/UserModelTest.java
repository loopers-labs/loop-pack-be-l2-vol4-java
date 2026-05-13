package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private static final String VALID_LOGIN_ID = "chanhee";
    private static final EncodedPassword VALID_PASSWORD = new EncodedPassword("chan1234!");
    private static final String VALID_NAME = "김찬희";
    private static final String VALID_BIRTH_DATE = "1995-05-10";
    private static final String VALID_EMAIL = "chan950510@gmail.com";

    @DisplayName("회원가입 시")
    @Nested
    class Create {

        @DisplayName("모든 정보가 입력되면 가입완료")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // arrange
            String loginId = VALID_LOGIN_ID;
            EncodedPassword password = VALID_PASSWORD;
            String name = VALID_NAME;
            String birthDate = VALID_BIRTH_DATE;
            String email = VALID_EMAIL;

            // act
            UserModel userModel = new UserModel(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                    () -> assertThat(userModel).isNotNull(),
                    () -> assertThat(userModel.getLoginId()).isEqualTo(loginId),
                    () -> assertThat(userModel.getName()).isEqualTo(name),
                    () -> assertThat(userModel.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("로그인ID에 영문/숫자 외 문자포함 시 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenLoginIdContainsInvalidCharacter() {
            // arrange
            String invalidLoginId = "chanhee!";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(invalidLoginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인ID가 비어있으면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            // arrange
            String blankLoginId = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(blankLoginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 null이면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenPasswordIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, null, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            String blankName = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, blankName, VALID_BIRTH_DATE, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식이 아니면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenBirthDateFormatIsInvalid() {
            // arrange
            String invalidBirthDate = "1995/05/10";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, invalidBirthDate, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 존재하지 않는 날짜이면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenBirthDateIsNotARealDate() {
            // arrange
            String invalidBirthDate = "1995-13-01";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, invalidBirthDate, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 비어있으면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenBirthDateIsBlank() {
            // arrange
            String blankBirthDate = "";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, blankBirthDate, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 aaa@bb.cc 형식이 아니면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange
            String invalidEmail = "id-test-email.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, invalidEmail);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 비어있으면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenEmailIsBlank() {
            // arrange
            String blankEmail = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, blankEmail);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
