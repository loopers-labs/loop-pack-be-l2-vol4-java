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

    private static final LoginId VALID_LOGIN_ID = new LoginId("loopers01");
    private static final String VALID_ENCODED_PASSWORD = "$2a$10$encodedPasswordHash";
    private static final UserName VALID_NAME = new UserName("홍길동");
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2002, 5, 11);
    private static final Email VALID_EMAIL = new Email("test@loopers.com");

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

        @DisplayName("이름 VO의 masked 결과를 반환한다")
        @Test
        void returnsMaskedNameFromValueObject() {
            // given
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, new UserName("홍길동"), VALID_BIRTH_DATE, VALID_EMAIL);

            // when
            String masked = user.getMaskedName();

            // then
            assertThat(masked).isEqualTo("홍길*");
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
