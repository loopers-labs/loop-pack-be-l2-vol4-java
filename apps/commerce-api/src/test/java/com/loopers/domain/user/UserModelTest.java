package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private static final String VALID_LOGIN_ID = "user123";
    private static final String VALID_PASSWORD = "Password1!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String VALID_EMAIL = "test@example.com";

    @DisplayName("회원 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 정보가 유효하게 주어지면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // arrange & act
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            assertAll(
                () -> assertThat(user.getId()).isNotNull(),
                () -> assertThat(user.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(user.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(user.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        // ── 로그인 ID 검증 ──────────────────────────────────────────────────────

        @DisplayName("로그인 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(null, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID가 빈칸이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("   ", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user@123", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsKorean() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("유저123", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID가 20자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdExceeds20Characters() {
            // arrange
            String loginId = "a".repeat(21);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(loginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        // ── 비밀번호 검증 ──────────────────────────────────────────────────────

        @DisplayName("비밀번호가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, null, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsShorterThan8Characters() {
            // arrange - 7자
            String password = "Pass1!A";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, password, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsLongerThan16Characters() {
            // arrange - 17자
            String password = "Password1!2345678";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, password, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않는 문자(한글)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // arrange
            String password = "패스워드1!AB";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, password, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd) 형식이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDateYYYYMMDD() {
            // arrange - VALID_BIRTH_DATE: 1990-01-15 → "19900115"
            String password = "Ab!19900115";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, password, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyMMdd) 형식이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDateYYMMDD() {
            // arrange - VALID_BIRTH_DATE: 1990-01-15 → "900115"
            String password = "Ab!900115Z";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, password, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(MMdd) 형식이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDateMMDD() {
            // arrange - VALID_BIRTH_DATE: 1990-01-15 → "0115"
            String password = "Ab!0115WXYZ";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, password, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        // ── 이름 검증 ──────────────────────────────────────────────────────────

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, null, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈칸이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, "   ", VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        // ── 생년월일 검증 ──────────────────────────────────────────────────────

        @DisplayName("생년월일이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, null, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        // ── 이메일 검증 ──────────────────────────────────────────────────────

        @DisplayName("이메일이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 빈칸이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "   ")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일에 '@'가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailHasNoAtSign() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "testexample.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일에 로컬 파트가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailHasNoLocalPart() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "@example.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일에 도메인이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailHasNoDomain() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "test@")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일에 TLD가 없으면 (zz@yy 형태), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailHasNoTLD() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "test@example")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름을 마스킹할 때, ")
    @Nested
    class GetMaskedName {

        @DisplayName("이름의 마지막 글자가 '*'로 마스킹되어 반환된다.")
        @Test
        void returnsMaskedName_withLastCharacterMasked() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertThat(maskedName).isEqualTo("홍길*");
        }

        @DisplayName("한 글자 이름이면, '*'만 반환된다.")
        @Test
        void returnsSingleAsterisk_whenNameHasOneCharacter() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, "홍", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertThat(maskedName).isEqualTo("*");
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 유효하면, 정상적으로 변경된다.")
        @Test
        void changesPassword_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act & assert
            assertDoesNotThrow(() -> user.changePassword(VALID_PASSWORD, "NewPass2@"));
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                user.changePassword("WrongPass1!", "NewPass2@")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                user.changePassword(VALID_PASSWORD, VALID_PASSWORD)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsShorterThan8Characters() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                user.changePassword(VALID_PASSWORD, "New1!AB")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsLongerThan16Characters() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                user.changePassword(VALID_PASSWORD, "NewPassword1!2345")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 허용되지 않는 문자(한글)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsInvalidCharacters() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                user.changePassword(VALID_PASSWORD, "새패스워드1!A")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일(yyyyMMdd) 형식이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDateYYYYMMDD() {
            // arrange - VALID_BIRTH_DATE: 1990-01-15 → "19900115"
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                user.changePassword(VALID_PASSWORD, "Ab!19900115")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일(yyMMdd) 형식이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDateYYMMDD() {
            // arrange - VALID_BIRTH_DATE: 1990-01-15 → "900115"
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                user.changePassword(VALID_PASSWORD, "Ab!900115Z")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일(MMdd) 형식이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthDateMMDD() {
            // arrange - VALID_BIRTH_DATE: 1990-01-15 → "0115"
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                user.changePassword(VALID_PASSWORD, "Ab!0115WXYZ")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
