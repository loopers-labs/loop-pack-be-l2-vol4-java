package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserModelTest {
    private static final String VALID_LOGIN_ID = "testId";
    private static final String VALID_PW = "validPassword123";
    private static final String VALID_NAME = "임찬빈";
    private static final String VALID_BIRTH_DATE = "1998-04-11";
    private static final String VALID_EMAIL = "test@test.com";

    @DisplayName("로그인 id에 특수문자가 포함되면, bad_request 예외가 발생한다.")
    @Test
    public void createBadRequest_whenLoginIdIsSpecialLetter() {
        // arrange
        String invalidLoginId = "$%%%";
        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(invalidLoginId, VALID_PW, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("pw가 20자를 넘어가면, BAD_REQUEST가 발생한다.")
    @Test
    void createBadRequest_whenPWIsOver20Letter() {
        // arrange
        String invalidPw = "asdfasdfasdfasdfasdf!@#$12341";
        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, invalidPw, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("이름이 비어있다면, BAD_REQUEST 예외가 발생한다.")
    @Test
    void createBadRequest_whenNameIsBlank() {
        // arrange
        String emptyName = "";
        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PW, emptyName, VALID_BIRTH_DATE, VALID_EMAIL));
        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("이메일 형식 aa@bb.com 에 맞지 않는다면 BAD_REQUEST가 발생한다.")
    @Test
    void createBadRequest_whenEmailIsNotRightSetting() {
        // arrange
        String invalidEmail = "@aaa.bbb";
        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PW, VALID_NAME, VALID_BIRTH_DATE, invalidEmail));
        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("생년월일 형식(1998-04-11)이 맞지 않으면 BAD_REQUEST가 발생한다.")
    @Test
    void createBadRequest_whenBirthDateIsNotRightSetting() {
        // arrange
        String invalidBirthDate = "1998-15-11";
        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, VALID_PW, VALID_NAME, invalidBirthDate, VALID_EMAIL));
        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
    @DisplayName("비밀번호 8자보다 작으면 BAD_REQUEST가 발생한다.")
    @Test
    void createBadRequest_whenPwSizeIsOver20(){
        // arrange
        String invalidPw = "asdf";
        // act
        CoreException result = assertThrows(CoreException.class,()->
                new UserModel(VALID_LOGIN_ID,invalidPw,VALID_NAME,VALID_BIRTH_DATE,VALID_EMAIL));
        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
    @DisplayName("비밀번호는 영문 대소문자 숫자 특수문자가 아닐경우, BAD_REQUEST가 발생합니다.")
    @Test
    void createBadRequest_whenPwIsNotCorrectSetting(){
        // arrange
        String invalidPw = "임찬빈123456";
        //act
        CoreException result = assertThrows(CoreException.class, () ->
                new UserModel(VALID_LOGIN_ID, invalidPw, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL));
        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
