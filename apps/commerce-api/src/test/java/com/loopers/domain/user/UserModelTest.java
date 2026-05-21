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

    @DisplayName("회원 모델을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // arrange
            String loginId = "user1234";
            String passwordHash = "$2a$10$hashedPassword";
            String name = "홍길동";
            LocalDate birth = LocalDate.of(1990, 1, 15);
            String email = "user@example.com";

            // act
            UserModel user = new UserModel(loginId, passwordHash, name, birth, email);

            // assert
            assertAll(
                () -> assertThat(user.getId()).isNotNull(),
                () -> assertThat(user.getLoginId()).isEqualTo(loginId),
                () -> assertThat(user.getPasswordHash()).isEqualTo(passwordHash),
                () -> assertThat(user.getName()).isEqualTo(name),
                () -> assertThat(user.getBirth()).isEqualTo(birth),
                () -> assertThat(user.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("로그인 ID가 영문과 숫자 외 문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdContainsInvalidCharacters() {
            // arrange
            String loginId = "user-123";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, "$2a$10$hashedPassword", "홍길동", LocalDate.of(1990, 1, 15), "user@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID 길이가 4자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdIsTooShort() {
            // arrange
            String loginId = "abc";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, "$2a$10$hashedPassword", "홍길동", LocalDate.of(1990, 1, 15), "user@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String name = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("user1234", "$2a$10$hashedPassword", name, LocalDate.of(1990, 1, 15), "user@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 미래 날짜이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthIsFutureDate() {
            // arrange
            LocalDate birth = LocalDate.now().plusDays(1);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("user1234", "$2a$10$hashedPassword", "홍길동", birth, "user@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailFormatIsInvalid() {
            // arrange
            String email = "invalid-email";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("user1234", "$2a$10$hashedPassword", "홍길동", LocalDate.of(1990, 1, 15), email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호 해시가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordHashIsBlank() {
            // arrange
            String passwordHash = "";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("user1234", passwordHash, "홍길동", LocalDate.of(1990, 1, 15), "user@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("회원 이름을 마스킹할 때, ")
    @Nested
    class MaskName {
        @DisplayName("마지막 1자를 별표로 바꾼다.")
        @Test
        void returnsMaskedName() {
            // arrange
            UserModel user = new UserModel(
                "user1234",
                "$2a$10$hashedPassword",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "user@example.com"
            );

            // act
            String result = user.maskedName();

            // assert
            assertThat(result).isEqualTo("홍길*");
        }

        @DisplayName("이름이 한 글자이면, 별표 하나를 반환한다.")
        @Test
        void returnsSingleAsterisk_whenNameHasOneCharacter() {
            // arrange
            UserModel user = new UserModel(
                "user1234",
                "$2a$10$hashedPassword",
                "홍",
                LocalDate.of(1990, 1, 15),
                "user@example.com"
            );

            // act
            String result = user.maskedName();

            // assert
            assertThat(result).isEqualTo("*");
        }
    }

    @DisplayName("비밀번호 해시를 변경할 때, ")
    @Nested
    class ChangePasswordHash {
        @DisplayName("유효한 해시가 주어지면, 비밀번호 해시를 변경한다.")
        @Test
        void changesPasswordHash_whenPasswordHashIsValid() {
            // arrange
            UserModel user = new UserModel(
                "user1234",
                "$2a$10$oldPasswordHash",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "user@example.com"
            );
            String newPasswordHash = "$2a$10$newPasswordHash";

            // act
            user.changePasswordHash(newPasswordHash);

            // assert
            assertThat(user.getPasswordHash()).isEqualTo(newPasswordHash);
        }
    }
}
