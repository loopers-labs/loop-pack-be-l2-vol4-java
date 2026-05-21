package com.loopers.tddstudy.dto;

import com.loopers.tddstudy.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserInfoResponseTest {

    @Test
    @DisplayName("마지막 글자를 * 로 마스킹한다")
    void maskLastChar() {
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");
        UserInfoResponse response = new UserInfoResponse(user);

        assertThat(response.name()).isEqualTo("김릴*");
    }

    @Test
    @DisplayName("비밀번호는 반환 정보에 포함되지 않는다")
    void doesNotContainPassword() {
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");
        UserInfoResponse response = new UserInfoResponse(user);

        assertThat(response.loginId()).isEqualTo("lilpa123");
        assertThat(response.name()).isEqualTo("김릴*");
        assertThat(response.birthDate()).isEqualTo("19901225");
        assertThat(response.email()).isEqualTo("lilpa@email.com");
    }




}
