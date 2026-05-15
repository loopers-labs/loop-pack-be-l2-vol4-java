package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 1);

    @DisplayName("회원을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("올바른 정보를 모두 입력하면 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllFieldsAreValid() {
            // arrange
            String loginId = "user123";
            String encodedPassword = "$2a$10$encodedpassword";
            String name = "홍길동";
            String email = "user@example.com";

            // act
            UserModel user = new UserModel(loginId, encodedPassword, name, BIRTH_DATE, email);

            // assert
            assertAll(
                () -> assertThat(user.getId()).isNotNull(),
                () -> assertThat(user.getLoginId()).isEqualTo(loginId),
                () -> assertThat(user.getName()).isEqualTo(name),
                () -> assertThat(user.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(user.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("로그인 ID 가 5자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsTooShort() {
            // arrange
            String loginId = "user";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(loginId, "encoded", "홍길동", BIRTH_DATE, "user@example.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID 가 20자 초과이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsTooLong() {
            // arrange
            String loginId = "a".repeat(21);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(loginId, "encoded", "홍길동", BIRTH_DATE, "user@example.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID 에 특수문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"user@123", "user 123", "user!23", "user_123"})
        void throwsBadRequest_whenLoginIdContainsSpecialChars(String loginId) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(loginId, "encoded", "홍길동", BIRTH_DATE, "user@example.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 한글이 아니면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"John", "홍John", "123", "홍 길"})
        void throwsBadRequest_whenNameIsNotKorean(String name) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user123", "encoded", name, BIRTH_DATE, "user@example.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 1자이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsTooShort() {
            // arrange
            String name = "홍";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user123", "encoded", name, BIRTH_DATE, "user@example.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 10자 초과이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsTooLong() {
            // arrange
            String name = "가".repeat(11);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user123", "encoded", name, BIRTH_DATE, "user@example.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user123", "encoded", "홍길동", null, "user@example.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 올바르지 않으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"notanemail", "missing@", "@nodomain.com", "no-at-sign"})
        void throwsBadRequest_whenEmailIsInvalid(String email) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new UserModel("user123", "encoded", "홍길동", BIRTH_DATE, email)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class UpdatePassword {

        @DisplayName("새 비밀번호로 업데이트된다.")
        @Test
        void updatesPassword() {
            // arrange
            UserModel user = new UserModel("user123", "encodedOld", "홍길동", BIRTH_DATE, "user@example.com");

            // act
            user.updatePassword("encodedNew");

            // assert
            assertThat(user.getPassword()).isEqualTo("encodedNew");
        }
    }

    @DisplayName("이름을 마스킹할 때,")
    @Nested
    class MaskName {

        @DisplayName("마지막 글자가 * 로 마스킹된다.")
        @Test
        void masksLastCharacter() {
            // arrange
            UserModel user = new UserModel("user123", "encoded", "홍길동", BIRTH_DATE, "user@example.com");

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertThat(maskedName).isEqualTo("홍길*");
        }

        @DisplayName("두 글자 이름도 마스킹된다.")
        @Test
        void masksLastCharacter_whenNameIsTwoChars() {
            // arrange
            UserModel user = new UserModel("user123", "encoded", "홍길", BIRTH_DATE, "user@example.com");

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertThat(maskedName).isEqualTo("홍*");
        }
    }
}
