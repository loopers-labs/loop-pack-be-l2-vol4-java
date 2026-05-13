package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    @DisplayName("User 객체를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, User 객체가 정상 생성된다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // arrange
            String loginId = "user1";
            String password = "Pass123!";
            String name = "홍길동";
            String email = "test@example.com";
            String birthDate = "2000-01-01";
            Gender gender = Gender.MALE;

            // act
            UserModel user = new UserModel(loginId, password, name, email, birthDate, gender);

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(loginId),
                () -> assertThat(user.getName()).isEqualTo(name),
                () -> assertThat(user.getEmail()).isEqualTo(email),
                () -> assertThat(user.getGender()).isEqualTo(gender)
            );
        }

        @DisplayName("loginId가 영문/숫자 외 문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsInvalidCharacter() {
            // arrange
            String loginId = "hello!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(loginId, "pass1234", "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsEmpty() {
            // arrange
            String loginId = "";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(loginId, "pass1234", "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId가 정확히 1자이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenLoginIdIsExactlyOneCharacter() {
            // arrange
            String loginId = "a";

            // act & assert
            assertDoesNotThrow(() ->
                new UserModel(loginId, "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );
        }

        @DisplayName("loginId가 정확히 10자이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenLoginIdIsExactlyTenCharacters() {
            // arrange
            String loginId = "abcde12345";

            // act & assert
            assertDoesNotThrow(() ->
                new UserModel(loginId, "pass1234", "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );
        }

        @DisplayName("loginId가 10자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdExceedsTenCharacters() {
            // arrange
            String loginId = "abcde12345X";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(loginId, "pass1234", "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일에 유효한 특수문자(+)가 포함되어도, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenEmailContainsValidSpecialCharacter() {
            // arrange
            String email = "user+tag@example.com";

            // act & assert
            assertDoesNotThrow(() ->
                new UserModel("user1", "Pass123!", "홍길동", email, "2000-01-01", Gender.MALE)
            );
        }

        @DisplayName("이메일이 올바른 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange
            String email = "invalid-email";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", "홍길동", email, "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일에 도메인 확장자가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailHasNoDomainExtension() {
            // arrange
            String email = "user@example";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", "홍길동", email, "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsEmpty() {
            // arrange
            String email = "";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", "홍길동", email, "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateFormatIsInvalid() {
            // arrange
            String birthDate = "20000101";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", "홍길동", "test@example.com", birthDate, Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsEmpty() {
            // arrange
            String birthDate = "";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", "홍길동", "test@example.com", birthDate, Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 존재하지 않는 날짜이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsInvalidDate() {
            // arrange
            String birthDate = "2000-02-30";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", "홍길동", "test@example.com", birthDate, Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 오늘 날짜이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsToday() {
            // arrange
            String birthDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "Pass123!", "홍길동", "test@example.com", birthDate, Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 미래 날짜이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsInFuture() {
            // arrange
            String birthDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", "홍길동", "test@example.com", birthDate, Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // arrange
            String name = null;

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", name, "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsEmpty() {
            // arrange
            String name = "";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", name, "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백만으로 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // arrange
            String name = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "pass1234", name, "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름에 숫자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsNumber() {
            // arrange
            String name = "홍길1";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "Pass123!", name, "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름에 영문이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsEnglish() {
            // arrange
            String name = "홍Gil동";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "Pass123!", name, "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsSpecialCharacter() {
            // arrange
            String name = "홍길!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "Pass123!", name, "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 1자이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsTooShort() {
            // arrange
            String name = "홍";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "Pass123!", name, "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 11자 이상이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsTooLong() {
            // arrange
            String name = "홍길동홍길동홍길동홍길"; // 11자

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", "Pass123!", name, "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 정확히 2자이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenNameIsExactlyTwoCharacters() {
            // arrange
            String name = "홍길";

            // act & assert
            assertDoesNotThrow(() ->
                new UserModel("user1", "Pass123!", name, "test@example.com", "2000-01-01", Gender.MALE)
            );
        }

        @DisplayName("이름이 정확히 10자이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenNameIsExactlyTenCharacters() {
            // arrange
            String name = "홍길동홍길동홍길동홍"; // 10자

            // act & assert
            assertDoesNotThrow(() ->
                new UserModel("user1", "Pass123!", name, "test@example.com", "2000-01-01", Gender.MALE)
            );
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            String password = "Pass12!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", password, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            String password = "Pass123!Pass12345";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", password, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsSpace() {
            // arrange
            String password = "Pass 123!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", password, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않는 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacter() {
            // arrange
            String password = "pass한글12";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", password, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            String password = "pass20000101";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user1", password, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 정확히 8자이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPasswordIsExactlyEightCharacters() {
            // arrange
            String password = "Pass123!";

            // act & assert
            assertDoesNotThrow(() ->
                new UserModel("user1", password, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );
        }

        @DisplayName("비밀번호가 정확히 16자이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPasswordIsExactlySixteenCharacters() {
            // arrange
            String password = "Pass123!Pass123!";

            // act & assert
            assertDoesNotThrow(() ->
                new UserModel("user1", password, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)
            );
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class ChangePassword {

        // 비밀번호 형식 규칙(길이, 허용 문자, 생년월일 포함 불가)은 회원가입과 동일한 validatePassword()를 사용하므로
        // 규칙에 대한 상세 검증은 Create 테스트에서 담당한다. 여기서는 변경 자체가 정상 동작하는지만 확인한다.
        @DisplayName("유효한 새 비밀번호이면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenNewPasswordIsValid() {
            // arrange
            UserModel user = new UserModel("user1", "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE);

            // act
            user.changePassword("NewPass1!");

            // assert
            assertThat(user.getPassword()).isEqualTo("NewPass1!");
        }
    }

    @DisplayName("이름을 마스킹할 때,")
    @Nested
    class MaskName {

        @DisplayName("이름이 두 글자 이상이면, 마지막 글자만 *로 마스킹된다.")
        @Test
        void masksLastCharacter_whenNameHasMultipleCharacters() {
            // arrange
            UserModel user = new UserModel("user1", "Pass123!", "홍길동", "test@example.com", "2000-01-01", Gender.MALE);

            // act
            String masked = user.getMaskedName();

            // assert
            assertThat(masked).isEqualTo("홍길*");
        }

        @DisplayName("이름이 두 글자이면, 마지막 글자만 *로 마스킹된다.")
        @Test
        void masksLastCharacter_whenNameIsTwoCharacters() {
            // arrange
            UserModel user = new UserModel("user1", "Pass123!", "홍길", "test@example.com", "2000-01-01", Gender.MALE);

            // act
            String masked = user.getMaskedName();

            // assert
            assertThat(masked).isEqualTo("홍*");
        }
    }
}
