package com.loopers.user.interfaces.api;

import com.loopers.user.application.UserResult;
import com.loopers.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserInfoResponseTest {

    private UserResult.Detail resultWithName(String name) {
        User user = User.create(
            "loopers01", "encoded-password", name, LocalDate.of(1995, 3, 21), "looper@example.com"
        );
        return UserResult.Detail.from(user);
    }

    @Test
    @DisplayName("이름의 마지막 글자를 *로 마스킹한다")
    void givenMultiCharacterName_whenFrom_thenMasksLastCharacter() {
        UserV1Response.Masked response = UserV1Response.Masked.from(resultWithName("김루퍼"));
        assertThat(response.name()).isEqualTo("김루*");
    }

    @Test
    @DisplayName("이름이 한 글자이면 그 글자를 *로 마스킹한다")
    void givenSingleCharacterName_whenFrom_thenMasksTheCharacter() {
        UserV1Response.Masked response = UserV1Response.Masked.from(resultWithName("김"));

        assertThat(response.name()).isEqualTo("*");
    }

    @Test
    @DisplayName("이름 외 필드는 그대로 복사한다")
    void givenUser_whenFrom_thenCopiesOtherFieldsAsIs() {
        UserV1Response.Masked response = UserV1Response.Masked.from(resultWithName("김루퍼"));

        assertThat(response.loginId()).isEqualTo("loopers01");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1995, 3, 21));
        assertThat(response.email()).isEqualTo("looper@example.com");
    }
}
