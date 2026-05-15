package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTest {

    @DisplayName("이름을 마스킹해 응답을 생성할 때, ")
    @Nested
    class FromMasked {
        @DisplayName("이름의 마지막 글자를 * 로 치환해 반환한다")
        @Test
        void returnsMaskedName_whenNameHasMultipleChars() {
            // given
            String name = "홍길동";
            UserInfo userInfo = new UserInfo(1L, "user01", name, "1990-01-01", "user@example.com");

            // when
            UserV1Dto.UserResponse response = UserV1Dto.UserResponse.fromMasked(userInfo);

            // then
            assertThat(response.name()).isEqualTo("홍길*");
        }

        @DisplayName("이름이 1글자이면 * 를 반환한다")
        @Test
        void returnsAsterisk_whenNameHasOneChar() {
            // given
            String name = "홍";
            UserInfo userInfo = new UserInfo(1L, "user01", name, "1990-01-01", "user@example.com");

            // when
            UserV1Dto.UserResponse response = UserV1Dto.UserResponse.fromMasked(userInfo);

            // then
            assertThat(response.name()).isEqualTo("*");
        }
    }
}
