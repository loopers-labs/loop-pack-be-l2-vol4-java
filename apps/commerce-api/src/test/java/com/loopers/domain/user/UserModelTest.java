package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    @DisplayName("User 객체를 생성할 때,")
    @Nested
    class Create {

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
    }
}
