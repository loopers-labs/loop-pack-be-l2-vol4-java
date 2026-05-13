package com.loopers.domain.user;

import com.loopers.fixture.UserFixture;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    @DisplayName("유저 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("loginId 가 영문/숫자 10자 이내 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdFormatIsInvalid() {
            // arrange — 잘못된 loginId 한 개만 두고, 나머지는 전부 VALID
            String invalidLoginId = "한글아이디";

            // act — UserModel 생성 시 CoreException 이 던져질 것을 기대
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(invalidLoginId, UserFixture.PASSWORD, UserFixture.NAME, UserFixture.BIRTH, UserFixture.EMAIL)
            );

            // assert — ErrorType 이 BAD_REQUEST 인지
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email 이 xx@yy.zz 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange — 잘못된 email 한 개만 두고, 나머지는 전부 VALID
            String invalidEmail = "invalid-email";

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(UserFixture.LOGIN_ID, UserFixture.PASSWORD, UserFixture.NAME, UserFixture.BIRTH, invalidEmail)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birth 가 yyyy-MM-dd 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthFormatIsInvalid() {
            // arrange — 잘못된 birth 한 개만 두고, 나머지는 전부 VALID (구분자가 다름)
            String invalidBirth = "1990/01/01";

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                new UserModel(UserFixture.LOGIN_ID, UserFixture.PASSWORD, UserFixture.NAME, invalidBirth, UserFixture.EMAIL)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password 가 8~16자 영문/숫자/특수문자 규칙에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordFormatIsInvalid() {
            // arrange — 잘못된 password 한 개만 두고, 나머지는 전부 VALID (구분자가 다름)
            String invalidPassword = "abc1!";

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                    new UserModel(UserFixture.LOGIN_ID, invalidPassword, UserFixture.NAME, UserFixture.BIRTH, UserFixture.EMAIL)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password 에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirth() {
            // arrange — password 가 birth("1990-01-01") 를 포함, 비밀번호 RULE 의 다른 조건(8~16자, 영문/숫자/특수문자)은 만족
            String passwordContainingBirth = "ab1990-01-01";   // 12자, 영문+숫자+하이픈 → 형식은 통과해버림

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                    new UserModel(UserFixture.LOGIN_ID, passwordContainingBirth, UserFixture.NAME, UserFixture.BIRTH, UserFixture.EMAIL)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
