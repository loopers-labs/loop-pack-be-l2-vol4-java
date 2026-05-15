package com.loopers.tddstudy.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("유효한 정보로 유저를 생성할 수 있다")
    void createUser_success() {
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        assertThat(user.getLoginId()).isEqualTo("lilpa123");
        assertThat(user.getName()).isEqualTo("김릴파");
        assertThat(user.getEmail()).isEqualTo("lilpa@email.com");
    }

    @Test
    @DisplayName("로그인 ID가 비어있으면 예외가 발생한다")
    void createUser_emptyLoginId_throwsException() {
        assertThatThrownBy(() -> new User("", "Pass1234!", "김릴파", "19901225", "lilpa@email.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 예외가 발생한다")
    void createUser_invalidEmail_throwsException() {
        assertThatThrownBy(() -> new User("lilpa123", "Pass1234!", "김릴파", "19901225", "invalid-email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 예외가 발생한다")
    void createUser_shortPassword_throwsException() {
        assertThatThrownBy(() -> new User("lilpa123", "Pa1!", "김릴파", "19901225", "lilpa@email.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호가 16자 초과이면 예외가 발생한다")
    void createUser_longPassword_throwsException() {
        assertThatThrownBy(() -> new User("lilpa123", "Pass1234!Pass1234!", "김릴파", "19901225", "lilpa@email.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호에 허용되지 않는 문자가 포함되면 예외가 발생한다")
    void createUser_invalidCharInPassword_throwsException() {
        assertThatThrownBy(() -> new User("lilpa123", "패스워드1234!", "김릴파", "19901225", "lilpa@email.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호에 생년월일이 포함되면 예외가 발생한다")
    void createUser_passwordContainsBirthDate_throwsException() {
        assertThatThrownBy(() -> new User("lilpa123", "19901225Ab!", "김릴파", "19901225", "lilpa@email.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("패스워드 일치 True")
    void matchesPassword_success(){
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        assertThat(user.matchesPassword("Pass1234!")).isTrue();
    }

    @Test
    @DisplayName("패스워드 불일지 False")
    void matchesPassword_fail(){
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        assertThat(user.matchesPassword("Wrong1234!")).isFalse();
    }



}