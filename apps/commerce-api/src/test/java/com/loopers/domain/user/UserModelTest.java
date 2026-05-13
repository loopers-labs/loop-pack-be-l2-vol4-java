package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private static final LoginId LOGIN_ID = new LoginId("kim99");
    private static final Name NAME = new Name("홍길동");
    private static final BirthDate BIRTH_DATE = new BirthDate(LocalDate.of(1999, 1, 1));
    private static final Email EMAIL = new Email("kim@loopers.com");
    private static final String ENCODED_PASSWORD = "$2a$10$encodedPasswordHashValue";

    @DisplayName("UserModel 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("loginId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(null, NAME, BIRTH_DATE, EMAIL, ENCODED_PASSWORD));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name 이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(LOGIN_ID, null, BIRTH_DATE, EMAIL, ENCODED_PASSWORD));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birthDate 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(LOGIN_ID, NAME, null, EMAIL, ENCODED_PASSWORD));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email 이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(LOGIN_ID, NAME, BIRTH_DATE, null, ENCODED_PASSWORD));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("encodedPassword 가 null 또는 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void throwsBadRequest_whenEncodedPasswordIsBlank(String encodedPassword) {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, encodedPassword));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("모든 필드가 정상이면 UserModel 이 생성된다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // act
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, ENCODED_PASSWORD);

            // assert
            assertThat(user)
                .extracting(
                    UserModel::getLoginId,
                    UserModel::getName,
                    UserModel::getBirthDate,
                    UserModel::getEmail,
                    UserModel::getEncodedPassword
                )
                .containsExactly(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, ENCODED_PASSWORD);
        }
    }

    @DisplayName("changeEncodedPassword 호출 시, ")
    @Nested
    class ChangeEncodedPassword {

        @DisplayName("새 해시값이 null 또는 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void throwsBadRequest_whenNewEncodedIsBlank(String newEncoded) {
            // arrange
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, ENCODED_PASSWORD);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> user.changeEncodedPassword(newEncoded));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정상적인 새 해시값이면 내부 해시값이 갱신된다.")
        @Test
        void replacesEncodedPassword_whenNewEncodedIsValid() {
            // arrange
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, ENCODED_PASSWORD);
            String newEncoded = "$2a$10$newHashedValue";

            // act
            user.changeEncodedPassword(newEncoded);

            // assert
            assertThat(user.getEncodedPassword()).isEqualTo(newEncoded);
        }
    }
}
