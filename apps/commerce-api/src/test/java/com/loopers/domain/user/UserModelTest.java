package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserModelTest {

    public String loginId;
    public String loginPassword;
    public String name;
    public LocalDate birthday;
    public String email;

    @BeforeEach
    public void setUp() {
        loginId = "loopers";
        loginPassword = "pAssWord1!";
        name = "루퍼스";
        birthday = LocalDate.parse("2000-01-01");
        email = "email@email.com";
    }

    @DisplayName("유저 모델을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유저 정보가 모두 주어지면, 정상적으로 생성된다.")
        @Test
        void createUserModel_whenValidInfoProvided() {
            // arrange
            // act
            User user = new User(loginId, loginPassword, name, birthday, email);

            // assert
            assertAll(
                    () -> assertThat(user.getLoginId()).isEqualTo(loginId),
                    () -> assertThat(user.getLoginPassword()).isEqualTo(loginPassword),
                    () -> assertThat(user.getName()).isEqualTo(name),
                    () -> assertThat(user.getBirthday()).isEqualTo(birthday),
                    () -> assertThat(user.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("로그인 아이디가 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdIsBlank() {
            // arrange
            String blankLoginId = "      ";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    new User(blankLoginId, loginPassword, name, birthday, email));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String blankName = "      ";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    new User(loginId, loginPassword, blankName, birthday, email));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름에 한글, 영문 외 숫자나 특수문자가 있으면 BAD_REQUEST 예외가 발생한다. 또한 연속 공백을 차단한다.")
        @ParameterizedTest
        @ValueSource(strings = {"루퍼스!", "루퍼스123", "loopers♥", "루    퍼스"})
        void throwsBadRequestException_whenNameIsInvalid(String invalidName) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    new User(loginId, loginPassword, invalidName, birthday, email));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 ***@***.*** 형식이 아니면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {"email@", "@email.com", "email@email", "@@@", "a!@abc.com", "aaaaa", "A@aaa", "a @acds.com", "가나다라@가나다라.com"})
        void throwsBadRequestException_whenEmailIsInvalid(String invalidEmail) {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    new User(loginId, loginPassword, name, birthday, invalidEmail));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 미래이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthdayIsFuture() {
            // arrange
            LocalDate futureBirthday = LocalDate.now().plusDays(1);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    new User(loginId, loginPassword, name, futureBirthday, email));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
