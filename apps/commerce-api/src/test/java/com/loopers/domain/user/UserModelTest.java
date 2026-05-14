package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class UserModelTest {

    private static final String DEFAULT_USER_ID = "usertest123";
    private static final String DEFAULT_NAME = "홍길동";
    private static final String DEFAULT_PASSWORD = "abc123!@#";
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(1995, 6, 10);
    private static final String DEFAULT_EMAIL = "test@naver.com";
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @DisplayName("유저 모델을 생성하는 테스트")
    @Nested
    class SignUp {
        String userId;
        String name;
        String password;
        LocalDate birthDate;
        String email;

        @BeforeEach
        void setup() {
            userId = DEFAULT_USER_ID;
            name = DEFAULT_NAME;
            password = DEFAULT_PASSWORD;
            birthDate = DEFAULT_BIRTH_DATE;
            email = DEFAULT_EMAIL;
        }

        @DisplayName("정상적인 요청이 온 경우, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenRequestIsValid() {
            // act
            UserModel userModel = new UserModel(userId, password, name, birthDate, email);

            // assert
            assertAll(
                () -> assertEquals(userId, userModel.getUserId()),
                () -> assertEquals(name, userModel.getName()),
                () -> assertEquals(birthDate, userModel.getBirthDate()),
                () -> assertEquals(email, userModel.getEmail()),
                () -> assertNotEquals(password, userModel.getPassword())
            );
        }

        @DisplayName("userId 가 빈 값인 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIsBlank() {
            // arrange
            userId = "";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("userId 에 특수문자가 포함된 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIncludesSpecialCharacter() {
            // arrange
            userId = "abdf$2341!!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이름이 빈 값인 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsBlank() {
            // arrange
            name = "";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이름에 특수문자가 포함된 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenNameIncludesSpecialCharacter() {
            // arrange
            name = "홍길!동";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이름에 공백이 포함된 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenNameIncludesSpace() {
            // arrange
            name = "홍 길동";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이메일 형식이 올바르지 않은 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenEmailFormatIsInvalid() {
            // arrange
            email = "abdfsd";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("비밀번호가 8자 미만인 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordIsTooShort() {
            // arrange
            password = "abc1!@#";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("비밀번호가 16자 초과인 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordIsTooLong() {
            // arrange
            password = "abcABC123!@#$%^&*";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("비밀번호에 생년월일 8자리(19950610)가 포함된 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordContainsFullBirthDate() {
            // arrange
            password = "19950610!A";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("비밀번호에 생년월일 6자리(950610)가 포함된 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordContainsYearlessBirthDate() {
            // arrange
            password = "950610!@Ab";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("비밀번호에 생년월일 4자리(0610)가 포함된 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordContainsMonthDayBirthDate() {
            // arrange
            password = "0610!@Abcd";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(userId, password, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }
    }

    @DisplayName("비밀번호 변경 테스트")
    @Nested
    class ChangePassword {
        UserModel userModel;

        @BeforeEach
        void setup() {
            userModel = new UserModel(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);
        }

        @DisplayName("올바른 현재 비밀번호와 새 비밀번호가 주어지면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenRequestIsValid() {
            // arrange
            String newPassword = "newPass1!@";

            // act
            userModel.changePassword(DEFAULT_PASSWORD, newPassword);

            // assert
            assertTrue(PASSWORD_ENCODER.matches(newPassword, userModel.getPassword()));
        }

        @DisplayName("현재 비밀번호가 일치하지 않는 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenCurrentPasswordNotMatches() {
            // arrange
            String wrongPassword = "wrongPass1!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userModel.changePassword(wrongPassword, "newPass1!@")
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일한 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenNewPasswordIsSameAsCurrent() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userModel.changePassword(DEFAULT_PASSWORD, DEFAULT_PASSWORD)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }
    }

    @DisplayName("이름 마스킹 테스트")
    @Nested
    class MaskName {

        @DisplayName("이름의 마지막 글자가 '*' 로 마스킹된다.")
        @Test
        void returnsMaskedName_whenNameHasThreeChars() {
            // arrange
            UserModel userModel = new UserModel(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);

            // act
            String maskedName = userModel.getMaskedName();

            // assert
            assertEquals("홍길*", maskedName);
        }

        @DisplayName("두 글자 이름의 마지막 글자가 '*' 로 마스킹된다.")
        @Test
        void returnsMaskedName_whenNameHasTwoChars() {
            // arrange
            UserModel userModel = new UserModel(DEFAULT_USER_ID, DEFAULT_PASSWORD, "홍길", DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);

            // act
            String maskedName = userModel.getMaskedName();

            // assert
            assertEquals("홍*", maskedName);
        }
    }

    @DisplayName("비밀번호 인증 테스트")
    @Nested
    class Authenticate {
        UserModel userModel;

        @BeforeEach
        void setup() {
            userModel = new UserModel(DEFAULT_USER_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);
        }

        @DisplayName("정상적인 비밀번호를 주면 인증이 성공한다.")
        @Test
        void authenticatesSuccessfully_whenPasswordIsCorrect() {
            // act & assert
            assertDoesNotThrow(() -> userModel.authenticate(DEFAULT_PASSWORD));
        }

        @DisplayName("잘못된 비밀번호를 입력하면 인증이 실패한다.")
        @Test
        void authenticatesFail_whenPasswordIsIncorrect() {
            // arrange
            String wrongPassword = "wrongPass1!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userModel.authenticate(wrongPassword)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }
    }
}
