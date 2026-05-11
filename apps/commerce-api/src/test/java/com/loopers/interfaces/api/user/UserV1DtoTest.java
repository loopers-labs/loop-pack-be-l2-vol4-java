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

        @DisplayName("이름의 마지막 글자를 * 로 마스킹한다.")
        @Test
        void masksLastCharOfName() {
            // arrange
            UserInfo info = new UserInfo(1L, "loopers01", "김루퍼스", LocalDate.of(1993, 11, 3), "loopers@example.com");

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

        @DisplayName("이름이 한 글자면, * 한 글자로 마스킹된다.")
        @Test
        void masksSingleCharName() {
            // arrange
            UserInfo info = new UserInfo(1L, "loopers01", "김", LocalDate.of(1993, 11, 3), "loopers@example.com");

            // act
            UserV1Dto.MyInfoResponse response = UserV1Dto.MyInfoResponse.from(info);

            // assert
            assertThat(response.name()).isEqualTo("*");
        }
    }
}