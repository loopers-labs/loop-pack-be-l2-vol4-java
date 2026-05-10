package com.loopers.domain.member;

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

public class MemberModelTest {
    String loginId;
    String loginPassword;
    String name ;
    LocalDate birthday;
    String email;


    @BeforeEach
    public void setUp() {
        loginId = "loopers";
        loginPassword = "pAssWord1!";
        name = "루퍼스";
        birthday = LocalDate.parse("2000-01-01");
        email = "email@email.com";

    }

    @DisplayName("회원 모델을 생성할 때,")
    @Nested
    class Create {
        @DisplayName("회원 정보가 모두 주어지면, 정상적으로 생성된다.")
        @Test
        void createUserModel_whenValidInfoProvided() {
            // arrange
            // act
            MemberModel memberModel = new MemberModel(loginId, loginPassword, name, birthday, email);

            // assert
            assertAll(
                    () -> assertThat(memberModel.getLoginId()).isEqualTo(loginId),
                    () -> assertThat(memberModel.getLoginPassword()).isEqualTo(loginPassword),
                    () -> assertThat(memberModel.getName()).isEqualTo(name),
                    () -> assertThat(memberModel.getBirthday()).isEqualTo(birthday),
                    () -> assertThat(memberModel.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("로그인 아이디가 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdIsBlank() {
            // arrange
            String blankLoginId = "      ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new MemberModel(blankLoginId, loginPassword, name, birthday, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 패스워드는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능하다.")
        @ParameterizedTest
        @ValueSource(strings = {"       ", "1234", "안녕하세요반갑습니다", "abcdefghijklmnopqrstuvwxyz"})
        void throwsBadRequestException_whenLoginPasswordIdIsInvalid(String invalidLoginPassword) {
            // arrange

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new MemberModel(loginId, invalidLoginPassword, name, birthday, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 패스워드에 생년월일이 포함되어 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Pass!20000101", "Pass!0101"})
        void throwsBadRequestException_whenLoginPasswordContainsBirthday(String loginPasswordWithBirthday) {
            // arrange

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new MemberModel(loginId, loginPasswordWithBirthday, name, birthday, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈칸으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String blankName = "      ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new MemberModel(loginId, loginPassword, blankName, birthday, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름에 한글, 영문 외 숫자나 특수문자가 있으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"루퍼스!", "루퍼스123", "loopers♥"})
        void throwsBadRequestException_whenNameIsInvalid(String invalidName) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new MemberModel(loginId, loginPassword, invalidName, birthday, email);
            });
            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 ***@***.*** 형식이 아니면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {"email@", "@email.com", "email@email", "@@@", "a!@abc.com", "aaaaa", "A@aaa"})
        void throwsBadRequestException_whenEmailIsInvalid(String invalidEmail) {
            // arrange

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new MemberModel(loginId, loginPassword, name, birthday, invalidEmail);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

}
