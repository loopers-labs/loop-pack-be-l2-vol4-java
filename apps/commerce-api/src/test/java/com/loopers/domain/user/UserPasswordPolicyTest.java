package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserPasswordPolicyTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 21);

    @Test
    @DisplayName("yyyyMMdd 형태의 생년월일이 비밀번호에 포함되면 true")
    void containsYyyyMMdd_returnsTrue() {
        assertThat(UserPasswordPolicy.containsBirthDate("19950321aA", BIRTH_DATE)).isTrue();
    }

    @Test
    @DisplayName("yyMMdd 형태의 생년월일이 비밀번호에 포함되면 true")
    void containsYyMMdd_returnsTrue() {
        assertThat(UserPasswordPolicy.containsBirthDate("950321aA!", BIRTH_DATE)).isTrue();
    }

    @Test
    @DisplayName("생년월일이 포함되지 않으면 false")
    void doesNotContain_returnsFalse() {
        assertThat(UserPasswordPolicy.containsBirthDate("Passw0rd!", BIRTH_DATE)).isFalse();
    }

    @Test
    @DisplayName("password 가 null 이면 false (null 자체는 별도 검증의 책임)")
    void nullPassword_returnsFalse() {
        assertThat(UserPasswordPolicy.containsBirthDate(null, BIRTH_DATE)).isFalse();
    }

    @Test
    @DisplayName("birthDate 가 null 이면 false")
    void nullBirthDate_returnsFalse() {
        assertThat(UserPasswordPolicy.containsBirthDate("Passw0rd!", null)).isFalse();
    }
}
