package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.*;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class UserEntityTest {

    private static final String DEFAULT_USER_ID = "usertest123";
    private static final String DEFAULT_NAME = "홍길동";
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(1995, 6, 10);
    private static final String DEFAULT_EMAIL = "test@naver.com";
    private static final PasswordVO DEFAULT_PASSWORD_VO = PasswordVO.fromEncoded("$2a$10$encodedHashValue");

    @DisplayName("유저 엔티티 생성")
    @Nested
    class SignUp {
        String userId;
        String name;
        LocalDate birthDate;
        String email;
        PasswordVO passwordVO;

        @BeforeEach
        void setup() {
            userId = DEFAULT_USER_ID;
            name = DEFAULT_NAME;
            birthDate = DEFAULT_BIRTH_DATE;
            email = DEFAULT_EMAIL;
            passwordVO = DEFAULT_PASSWORD_VO;
        }

        @DisplayName("정상적인 요청이면 유저 엔티티가 생성된다")
        @Test
        void createsUserEntity_whenRequestIsValid() {
            // act
            UserEntity user = new UserEntity(userId, passwordVO, name, birthDate, email);

            // assert
            assertAll(
                () -> assertEquals(userId, user.getUserId()),
                () -> assertEquals(name, user.getName()),
                () -> assertEquals(birthDate, user.getBirthDate()),
                () -> assertEquals(email, user.getEmail()),
                () -> assertEquals(passwordVO.value(), user.getPassword())
            );
        }

        @DisplayName("userId가 빈 값이면 예외가 발생한다")
        @Test
        void throwsException_whenUserIdIsBlank() {
            // arrange
            userId = "";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserEntity(userId, passwordVO, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("userId에 특수문자가 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenUserIdIncludesSpecialCharacter() {
            // arrange
            userId = "abdf$2341!!";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserEntity(userId, passwordVO, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이름이 빈 값이면 예외가 발생한다")
        @Test
        void throwsException_whenNameIsBlank() {
            // arrange
            name = "";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserEntity(userId, passwordVO, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이름에 특수문자가 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenNameIncludesSpecialCharacter() {
            // arrange
            name = "홍길!동";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserEntity(userId, passwordVO, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이름에 공백이 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenNameIncludesSpace() {
            // arrange
            name = "홍 길동";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserEntity(userId, passwordVO, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이메일 형식이 올바르지 않으면 예외가 발생한다")
        @Test
        void throwsException_whenEmailFormatIsInvalid() {
            // arrange
            email = "abdfsd";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserEntity(userId, passwordVO, name, birthDate, email)
            );

            // assert
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {
        UserEntity userModel;

        @BeforeEach
        void setup() {
            userModel = new UserEntity(DEFAULT_USER_ID, DEFAULT_PASSWORD_VO, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);
        }

        @DisplayName("새 PasswordVO가 주어지면 비밀번호가 변경된다")
        @Test
        void changesPassword_whenPasswordVOIsProvided() {
            // arrange
            PasswordVO newPasswordVO = PasswordVO.fromEncoded("$2a$10$newEncodedHash");

            // act
            userModel.changePassword(newPasswordVO);

            // assert
            assertEquals("$2a$10$newEncodedHash", userModel.getPassword());
        }
    }

    @DisplayName("이름 마스킹")
    @Nested
    class MaskName {

        @DisplayName("이름의 마지막 글자가 '*'로 마스킹된다")
        @Test
        void returnsMaskedName_whenNameHasThreeChars() {
            // arrange
            UserEntity user = new UserEntity(DEFAULT_USER_ID, DEFAULT_PASSWORD_VO, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertEquals("홍길*", maskedName);
        }

        @DisplayName("두 글자 이름의 마지막 글자가 '*'로 마스킹된다")
        @Test
        void returnsMaskedName_whenNameHasTwoChars() {
            // arrange
            UserEntity user = new UserEntity(DEFAULT_USER_ID, DEFAULT_PASSWORD_VO, "홍길", DEFAULT_BIRTH_DATE, DEFAULT_EMAIL);

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertEquals("홍*", maskedName);
        }
    }
}
