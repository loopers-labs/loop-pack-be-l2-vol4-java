package com.loopers.interfaces.api.user;

import com.loopers.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserInfoResponseTest {

    private User userWithName(String name) {
        return User.create(
            "loopers01", "encoded-password", name, LocalDate.of(1995, 3, 21), "looper@example.com"
        );
    }

    @Test
    @DisplayName("이름의 마지막 글자를 *로 마스킹한다")
    void from_masksLastCharacterOfName() {
        UserV1Dto.UserInfoResponse response = UserV1Dto.UserInfoResponse.from(userWithName("김루퍼"));
        assertThat(response.name()).isEqualTo("김루*");
    }

    @Test
    @DisplayName("이름이 한 글자이면 그 글자를 *로 마스킹한다")
    void from_masksSingleCharacterName() {
        UserV1Dto.UserInfoResponse response = UserV1Dto.UserInfoResponse.from(userWithName("김"));

        assertThat(response.name()).isEqualTo("*");
    }

    @Test
    @DisplayName("이름 외 필드는 그대로 복사한다")
    void from_copiesOtherFieldsAsIs() {
        UserV1Dto.UserInfoResponse response = UserV1Dto.UserInfoResponse.from(userWithName("김루퍼"));

        assertThat(response.loginId()).isEqualTo("loopers01");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1995, 3, 21));
        assertThat(response.email()).isEqualTo("looper@example.com");
    }
}
