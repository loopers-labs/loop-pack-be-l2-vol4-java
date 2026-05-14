package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserModelTest {

    @DisplayName("이름을 마스킹할 때,")
    @Nested
    class MaskName {

        @DisplayName("이름의 마지막 글자가 *로 마스킹되어 반환된다.")
        @Test
        void masksLastCharacterOfName() {
            // arrange
            UserModel user = new UserModel(
                "user01",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "user@example.com"
            );

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertThat(maskedName).isEqualTo("홍길*");
        }
    }
}
