package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserModelTest {

    private static final LoginId LOGIN_ID = new LoginId("kim99");
    private static final Name NAME = new Name("홍길동");
    private static final BirthDate BIRTH_DATE = new BirthDate(LocalDate.of(1999, 1, 1));
    private static final Email EMAIL = new Email("kim@loopers.com");
    private static final String RAW_PASSWORD = "Abcd123!";
    private static final String ENCODED_VALUE = "$2a$10$encodedPasswordHashValue";

    @Mock
    private PasswordEncoder passwordEncoder;

    private EncodedPassword encodedPassword;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_VALUE);
        encodedPassword = EncodedPassword.create(passwordEncoder, RAW_PASSWORD);
    }

    @DisplayName("UserModel 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("loginId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(null, NAME, BIRTH_DATE, EMAIL, encodedPassword));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name 이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(LOGIN_ID, null, BIRTH_DATE, EMAIL, encodedPassword));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birthDate 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(LOGIN_ID, NAME, null, EMAIL, encodedPassword));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email 이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(LOGIN_ID, NAME, BIRTH_DATE, null, encodedPassword));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("encodedPassword 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEncodedPasswordIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("모든 필드가 정상이면 UserModel 이 생성된다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // act
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, encodedPassword);

            // assert
            assertThat(user)
                .extracting(
                    UserModel::getLoginId,
                    UserModel::getName,
                    UserModel::getBirthDate,
                    UserModel::getEmail,
                    UserModel::getEncodedPassword
                )
                .containsExactly(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, encodedPassword);
        }
    }

    @DisplayName("matchesPassword 호출 시, ")
    @Nested
    class MatchesPassword {

        @DisplayName("저장된 해시와 일치하면 true 를 반환한다.")
        @Test
        void returnsTrue_whenEncoderReportsMatch() {
            // arrange
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, encodedPassword);
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_VALUE)).thenReturn(true);

            // act
            boolean result = user.matchesPassword(RAW_PASSWORD, passwordEncoder);

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("저장된 해시와 불일치하면 false 를 반환한다.")
        @Test
        void returnsFalse_whenEncoderReportsMismatch() {
            // arrange
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, encodedPassword);
            when(passwordEncoder.matches("Wrong123!", ENCODED_VALUE)).thenReturn(false);

            // act
            boolean result = user.matchesPassword("Wrong123!", passwordEncoder);

            // assert
            assertThat(result).isFalse();
        }
    }

    @DisplayName("doesNotMatchPassword 호출 시, ")
    @Nested
    class DoesNotMatchPassword {

        @DisplayName("저장된 해시와 일치하면 false 를 반환한다.")
        @Test
        void returnsFalse_whenEncoderReportsMatch() {
            // arrange
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, encodedPassword);
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_VALUE)).thenReturn(true);

            // act
            boolean result = user.doesNotMatchPassword(RAW_PASSWORD, passwordEncoder);

            // assert
            assertThat(result).isFalse();
        }

        @DisplayName("저장된 해시와 불일치하면 true 를 반환한다.")
        @Test
        void returnsTrue_whenEncoderReportsMismatch() {
            // arrange
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, encodedPassword);
            when(passwordEncoder.matches("Wrong123!", ENCODED_VALUE)).thenReturn(false);

            // act
            boolean result = user.doesNotMatchPassword("Wrong123!", passwordEncoder);

            // assert
            assertThat(result).isTrue();
        }
    }

    @DisplayName("changePassword 호출 시, ")
    @Nested
    class ChangePassword {

        @DisplayName("새 비밀번호가 null 또는 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void throwsBadRequest_whenNewRawPasswordIsBlank(String newRaw) {
            // arrange
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, encodedPassword);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> user.changePassword(passwordEncoder, newRaw));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정상적인 새 비밀번호이면 내부 해시값이 갱신된다.")
        @Test
        void replacesEncodedPassword_whenNewRawPasswordIsValid() {
            // arrange
            UserModel user = new UserModel(LOGIN_ID, NAME, BIRTH_DATE, EMAIL, encodedPassword);
            String newRaw = "NewPass1!";
            String newEncodedValue = "$2a$10$newHashedValue";
            when(passwordEncoder.encode(newRaw)).thenReturn(newEncodedValue);

            // act
            user.changePassword(passwordEncoder, newRaw);

            // assert
            assertThat(user.getEncodedPassword().getValue()).isEqualTo(newEncodedValue);
        }
    }
}
