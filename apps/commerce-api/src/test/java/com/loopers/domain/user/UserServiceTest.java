package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 1);
    private static final String RAW_PASSWORD = "Password1!";
    private static final String NEW_PASSWORD = "Password2!";

    @DisplayName("사용자를 등록할 때,")
    @Nested
    class RegisterUser {

        @DisplayName("사용자 정보가 유효하면, 사용자를 저장하고 반환한다.")
        @Test
        void registersUser_whenUserInfoIsValid() {
            // arrange
            FakeUserRepository userRepository = new FakeUserRepository();
            UserService userService = createUserService(userRepository);

            // act
            UserModel result = userService.registerUser(
                "user1",
                RAW_PASSWORD,
                "홍길동",
                BIRTH_DATE,
                "user1@example.com"
            );

            // assert
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo("user1"),
                () -> assertThat(result.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(result.getEmail()).isEqualTo("user1@example.com"),
                () -> assertThat(userRepository.findByUserId("user1")).containsSame(result)
            );
        }

        @DisplayName("사용자 정보가 유효하면, 비밀번호는 평문이 아닌 암호화된 값으로 저장한다.")
        @Test
        void storesEncodedPassword_whenUserInfoIsValid() {
            // arrange
            FakeUserRepository userRepository = new FakeUserRepository();
            FakePasswordEncryptor passwordEncryptor = new FakePasswordEncryptor();
            UserService userService = new UserService(userRepository, passwordEncryptor);

            // act
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // assert
            UserModel savedUser = userRepository.findByUserId("user1").orElseThrow();
            assertAll(
                () -> assertThat(savedUser.getEncodedPassword()).isNotEqualTo(RAW_PASSWORD),
                () -> assertThat(passwordEncryptor.matches(RAW_PASSWORD, savedUser.getEncodedPassword())).isTrue()
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, USER_ALREADY_EXISTS 예외가 발생한다.")
        @Test
        void throwsAlreadyExists_whenLoginIdAlreadyExists() {
            // arrange
            FakeUserRepository userRepository = new FakeUserRepository();
            UserService userService = createUserService(userRepository);
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", NEW_PASSWORD, "김루프", BIRTH_DATE, "user2@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS);
        }

        @DisplayName("로그인 ID가 영문과 숫자 외 문자를 포함하면, USER_ID_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidLoginId_whenLoginIdContainsInvalidCharacters() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user-1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ID_INVALID_FORMAT);
        }

        @DisplayName("비밀번호가 8자 미만이면, PASSWORD_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenPasswordLengthIsTooShort() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", "Aa1!", "홍길동", BIRTH_DATE, "user1@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_INVALID_FORMAT);
        }

        @DisplayName("비밀번호가 16자를 초과하면, PASSWORD_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenPasswordLengthIsTooLong() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", "Password123456789!", "홍길동", BIRTH_DATE, "user1@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_INVALID_FORMAT);
        }

        @DisplayName("비밀번호가 허용되지 않은 문자를 포함하면, PASSWORD_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenPasswordContainsUnsupportedCharacter() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", "Password1 ", "홍길동", BIRTH_DATE, "user1@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_INVALID_FORMAT);
        }

        @DisplayName("비밀번호가 허용되지 않은 특수문자를 포함하면, PASSWORD_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenPasswordContainsUnsupportedSpecialCharacter() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", "Password1_", "홍길동", BIRTH_DATE, "user1@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_INVALID_FORMAT);
        }

        @DisplayName("비밀번호가 생년월일을 포함하면, PASSWORD_CONTAINS_BIRTH_DATE 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenPasswordContainsBirthDate() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", "Abcd19900101!", "홍길동", BIRTH_DATE, "user1@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @DisplayName("이름이 null이면, USER_NAME_REQUIRED 예외가 발생한다.")
        @Test
        void throwsUserNameRequired_whenNameIsNull() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", RAW_PASSWORD, null, BIRTH_DATE, "user1@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NAME_REQUIRED);
        }

        @DisplayName("이름이 blank이면, USER_NAME_REQUIRED 예외가 발생한다.")
        @Test
        void throwsUserNameRequired_whenNameIsBlank() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", RAW_PASSWORD, "   ", BIRTH_DATE, "user1@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NAME_REQUIRED);
        }

        @DisplayName("생년월일이 null이면, BIRTH_DATE_REQUIRED 예외가 발생한다.")
        @Test
        void throwsBirthDateRequired_whenBirthDateIsNull() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", RAW_PASSWORD, "홍길동", null, "user1@example.com");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BIRTH_DATE_REQUIRED);
        }

        @DisplayName("이메일이 null이면, EMAIL_REQUIRED 예외가 발생한다.")
        @Test
        void throwsEmailRequired_whenEmailIsNull() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, null);
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EMAIL_REQUIRED);
        }

        @DisplayName("이메일이 blank이면, EMAIL_REQUIRED 예외가 발생한다.")
        @Test
        void throwsEmailRequired_whenEmailIsBlank() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "   ");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EMAIL_REQUIRED);
        }

        @DisplayName("이메일이 일반적인 이메일 형식이 아니면, EMAIL_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidEmail_whenEmailFormatIsInvalid() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@email");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EMAIL_INVALID_FORMAT);
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("로그인 ID와 비밀번호가 일치하면, 내 정보를 반환한다.")
        @Test
        void returnsMyInfo_whenLoginHeadersAreValid() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            UserModel result = userService.getUser("user1", RAW_PASSWORD);

            // assert
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo("user1"),
                () -> assertThat(result.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(result.getEmail()).isEqualTo("user1@example.com")
            );
        }

        @DisplayName("내 정보 조회 결과의 이름은 마지막 글자가 마스킹된다.")
        @Test
        void returnsMaskedName_whenMyInfoIsReturned() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            UserModel result = userService.getUser("user1", RAW_PASSWORD);

            // assert
            assertThat(result.getMaskedName()).isEqualTo("홍길*");
        }

        @DisplayName("가입되지 않은 로그인 ID로 조회하면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getUser("unknown", RAW_PASSWORD);
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면, PASSWORD_MISMATCH 예외가 발생한다.")
        @Test
        void throwsAuthenticationFailed_whenPasswordDoesNotMatch() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getUser("user1", "Wrong1!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면, 비밀번호를 변경한다.")
        @Test
        void changesPassword_whenCurrentPasswordAndNewPasswordAreValid() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            UserModel result = userService.changePassword("user1", RAW_PASSWORD, NEW_PASSWORD);

            // assert
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo("user1"),
                () -> assertDoesNotThrow(() -> userService.getUser("user1", NEW_PASSWORD)),
                () -> {
                    CoreException exception = assertThrows(CoreException.class, () -> {
                        userService.getUser("user1", RAW_PASSWORD);
                    });
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
                }
            );
        }

        @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면, 새 비밀번호도 암호화된 값으로 저장한다.")
        @Test
        void storesEncodedNewPassword_whenPasswordIsChanged() {
            // arrange
            FakeUserRepository userRepository = new FakeUserRepository();
            FakePasswordEncryptor passwordEncryptor = new FakePasswordEncryptor();
            UserService userService = new UserService(userRepository, passwordEncryptor);
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");
            String previousEncodedPassword = userRepository.findByUserId("user1").orElseThrow().getEncodedPassword();

            // act
            userService.changePassword("user1", RAW_PASSWORD, NEW_PASSWORD);

            // assert
            String changedEncodedPassword = userRepository.findByUserId("user1").orElseThrow().getEncodedPassword();
            assertAll(
                () -> assertThat(changedEncodedPassword).isNotEqualTo(previousEncodedPassword),
                () -> assertThat(changedEncodedPassword).isNotEqualTo(NEW_PASSWORD),
                () -> assertThat(passwordEncryptor.matches(NEW_PASSWORD, changedEncodedPassword)).isTrue(),
                () -> assertThat(passwordEncryptor.matches(RAW_PASSWORD, changedEncodedPassword)).isFalse()
            );
        }

        @DisplayName("가입되지 않은 로그인 ID로 비밀번호를 수정하면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenChangingPasswordForUnknownLoginId() {
            // arrange
            UserService userService = createUserService();

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("unknown", RAW_PASSWORD, NEW_PASSWORD);
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, PASSWORD_MISMATCH 예외가 발생한다.")
        @Test
        void throwsAuthenticationFailed_whenCurrentPasswordDoesNotMatch() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1", "Wrong1!", NEW_PASSWORD);
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, PASSWORD_SAME_AS_CURRENT 예외가 발생한다.")
        @Test
        void throwsSamePassword_whenNewPasswordIsSameAsCurrentPassword() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1", RAW_PASSWORD, RAW_PASSWORD);
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT);
        }

        @DisplayName("새 비밀번호가 8자 미만이면, PASSWORD_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenNewPasswordLengthIsTooShort() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1", RAW_PASSWORD, "Aa1!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_INVALID_FORMAT);
        }

        @DisplayName("새 비밀번호가 16자를 초과하면, PASSWORD_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenNewPasswordLengthIsTooLong() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1", RAW_PASSWORD, "Password123456789!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_INVALID_FORMAT);
        }

        @DisplayName("새 비밀번호가 허용되지 않은 문자를 포함하면, PASSWORD_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenNewPasswordContainsUnsupportedCharacter() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1", RAW_PASSWORD, "Password1 ");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_INVALID_FORMAT);
        }

        @DisplayName("새 비밀번호가 허용되지 않은 특수문자를 포함하면, PASSWORD_INVALID_FORMAT 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenNewPasswordContainsUnsupportedSpecialCharacter() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1", RAW_PASSWORD, "Password1_");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_INVALID_FORMAT);
        }

        @DisplayName("새 비밀번호가 생년월일을 포함하면, PASSWORD_CONTAINS_BIRTH_DATE 예외가 발생한다.")
        @Test
        void throwsInvalidPassword_whenNewPasswordContainsBirthDate() {
            // arrange
            UserService userService = createUserService();
            userService.registerUser("user1", RAW_PASSWORD, "홍길동", BIRTH_DATE, "user1@example.com");

            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("user1", RAW_PASSWORD, "Abcd19900101!");
            });

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }

    private UserService createUserService() {
        return createUserService(new FakeUserRepository());
    }

    private UserService createUserService(FakeUserRepository userRepository) {
        return new UserService(userRepository, new FakePasswordEncryptor());
    }

    private static class FakeUserRepository implements UserRepository {
        private final Map<String, UserModel> users = new HashMap<>();

        @Override
        public UserModel save(UserModel user) {
            users.put(user.getUserId(), user);
            return user;
        }

        @Override
        public Optional<UserModel> findByUserId(String userId) {
            return Optional.ofNullable(users.get(userId));
        }
    }

    private static class FakePasswordEncryptor implements PasswordEncryptor {
        private int sequence = 0;

        @Override
        public String encode(String rawPassword) {
            sequence += 1;
            return "encoded:" + sequence + ":" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return rawPassword != null
                && encodedPassword != null
                && encodedPassword.endsWith(":" + rawPassword);
        }
    }
}
