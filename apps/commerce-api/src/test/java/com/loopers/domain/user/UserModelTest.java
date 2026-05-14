package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private static final String VALID_LOGIN_ID = "user123";
    private static final String VALID_ENCODED_PW = "encodedPassword!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String VALID_EMAIL = "test@example.com";

    @DisplayName("UserModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("Given 유효한 모든 필드 / When 생성 / Then 정상 생성된다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // arrange & act
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(user.getPassword()).isEqualTo(VALID_ENCODED_PW),
                () -> assertThat(user.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(user.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("loginId 검증")
        @Nested
        class LoginId {

            @DisplayName("Given null loginId / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenLoginIdIsNull() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(null, VALID_ENCODED_PW, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("Given 공백 loginId / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenLoginIdIsBlank() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel("   ", VALID_ENCODED_PW, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("Given 특수문자 포함 loginId / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenLoginIdContainsSpecialCharacter() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel("user_123!", VALID_ENCODED_PW, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("password 검증")
        @Nested
        class Password {

            @DisplayName("Given null password / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenPasswordIsNull() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, null, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("Given 공백 password / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenPasswordIsBlank() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, "   ", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("name 검증")
        @Nested
        class Name {

            @DisplayName("Given null name / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenNameIsNull() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, null, VALID_BIRTH_DATE, VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("Given 공백 name / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenNameIsBlank() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, "  ", VALID_BIRTH_DATE, VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("birthDate 검증")
        @Nested
        class BirthDate {

            @DisplayName("Given null birthDate / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenBirthDateIsNull() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, VALID_NAME, null, VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("Given 오늘 날짜 birthDate / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenBirthDateIsToday() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, VALID_NAME, LocalDate.now(), VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("Given 미래 birthDate / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenBirthDateIsInFuture() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, VALID_NAME, LocalDate.now().plusDays(1), VALID_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("email 검증")
        @Nested
        class Email {

            @DisplayName("Given null email / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenEmailIsNull() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, VALID_NAME, VALID_BIRTH_DATE, null)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("Given 공백 email / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenEmailIsBlank() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, VALID_NAME, VALID_BIRTH_DATE, "   ")
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("Given 잘못된 형식의 email / When 생성 / Then BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenEmailFormatIsInvalid() {
                // arrange & act
                CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, VALID_NAME, VALID_BIRTH_DATE, "not-an-email")
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }
    }

    @DisplayName("maskedName()을 호출할 때,")
    @Nested
    class MaskedName {

        @DisplayName("Given 2자 이상 이름 / When 마스킹 / Then 마지막 글자만 '*'로 치환된다.")
        @Test
        void masksLastCharacter_whenNameHasMultipleCharacters() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL);

            // act & assert
            assertThat(user.maskedName()).isEqualTo("홍길*");
        }

        @DisplayName("Given 1자 이름 / When 마스킹 / Then 전체가 '*'로 치환된다.")
        @Test
        void masksEntireName_whenNameHasSingleCharacter() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PW, "김", VALID_BIRTH_DATE, VALID_EMAIL);

            // act & assert
            assertThat(user.maskedName()).isEqualTo("*");
        }
    }
}
