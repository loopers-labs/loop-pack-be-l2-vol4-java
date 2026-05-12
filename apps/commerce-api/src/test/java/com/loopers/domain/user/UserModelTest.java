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

    private static final String VALID_LOGIN_ID = "loopers01";
    private static final String VALID_ENCODED_PASSWORD = "$2a$10$encodedPasswordHash";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2002, 5, 11);
    private static final String VALID_EMAIL = "test@loopers.com";

    @DisplayName("유저 모델 생성 시")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면 유저 모델이 정상 생성된다")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // given
            // when
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // then
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(user.getEncodedPassword()).isEqualTo(VALID_ENCODED_PASSWORD),
                () -> assertThat(user.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(user.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("로그인 ID가 8자 미만이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenLoginIdIsShorterThanEightCharacters() {
            // given
            String shortLoginId = "loop123";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(shortLoginId, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID가 16자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenLoginIdIsLongerThanSixteenCharacters() {
            // given
            String longLoginId = "abcdefghij1234567";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(longLoginId, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID에 영문/숫자가 아닌 문자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenLoginIdContainsNonAlphanumeric() {
            // given
            String invalidLoginId = "loopers@01";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(invalidLoginId, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름에 숫자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNameContainsDigit() {
            // given
            String invalidName = "홍길동1";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, invalidName, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름에 특수문자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNameContainsSpecialCharacter() {
            // given
            String invalidName = "홍길동!";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, invalidName, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 21자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNameIsLongerThanTwentyCharacters() {
            // given
            String longName = "가".repeat(21);

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, longName, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 올바르지 않으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // given
            String invalidEmail = "loopers-without-at-sign";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, invalidEmail)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // given
            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, VALID_NAME, null, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("암호화된 비밀번호가 비어 있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenEncodedPasswordIsBlank() {
            // given
            String blankPassword = "  ";

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, blankPassword, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름 마스킹 시")
    @Nested
    class MaskName {

        @DisplayName("이름이 두 글자 이상이면 마지막 글자가 *로 치환된다")
        @Test
        void masksLastCharacter_whenNameHasMultipleCharacters() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL);

            // when
            String masked = user.maskedName();

            // then
            assertThat(masked).isEqualTo("홍길*");
        }

        @DisplayName("이름이 한 글자면 *만 반환된다")
        @Test
        void returnsAsteriskOnly_whenNameIsSingleCharacter() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, "김", VALID_BIRTH_DATE, VALID_EMAIL);

            // when
            String masked = user.maskedName();

            // then
            assertThat(masked).isEqualTo("*");
        }
    }

    @DisplayName("비밀번호 변경 시")
    @Nested
    class ChangePassword {

        @DisplayName("새 암호화된 비밀번호를 주면 정상적으로 변경된다")
        @Test
        void changesPassword_whenNewEncodedPasswordIsGiven() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            String newEncoded = "$2a$10$newEncodedHash";

            // when
            user.changePassword(newEncoded);

            // then
            assertThat(user.getEncodedPassword()).isEqualTo(newEncoded);
        }

        @DisplayName("새 비밀번호가 비어 있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNewEncodedPasswordIsBlank() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // when
            CoreException ex = assertThrows(CoreException.class, () -> user.changePassword(""));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
