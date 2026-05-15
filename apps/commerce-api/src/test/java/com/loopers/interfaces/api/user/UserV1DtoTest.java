package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class UserV1DtoTest {

    @DisplayName("MyInfoResponse 생성 시, ")
    @Nested
    class MyInfoResponseCreate {

        @DisplayName("UserInfo 의 maskedName 을 name 필드로 노출한다.")
        @Test
        void exposesMaskedNameFromUserInfo() {
            // arrange
            UserInfo info = new UserInfo(1L, "loopers01", "김루퍼스", "김루퍼*", LocalDate.of(1993, 11, 3), "loopers@example.com");

            // act
            UserV1Dto.MyInfoResponse response = UserV1Dto.MyInfoResponse.from(info);

            // assert
            assertAll(
                () -> assertThat(response.loginId()).isEqualTo("loopers01"),
                () -> assertThat(response.name()).isEqualTo("김루퍼*"),
                () -> assertThat(response.birthDate()).isEqualTo(LocalDate.of(1993, 11, 3)),
                () -> assertThat(response.email()).isEqualTo("loopers@example.com")
            );
        }
    }
}