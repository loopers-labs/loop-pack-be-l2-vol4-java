package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserNameTest {

    @DisplayName("UserName 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 이름이면 정상 생성된다.")
        @Test
        void creates_whenValid() {
            // arrange
            String value = "홍길동";

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.value()).isEqualTo(value);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> new UserName(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBlank() {
            // arrange & act & assert
            assertThatThrownBy(() -> new UserName("   "))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("마스킹 시,")
    @Nested
    class Masked {

        @DisplayName("마지막 글자가 *로 치환된다.")
        @Test
        void masksLastCharacter() {
            // arrange
            UserName userName = new UserName("홍길동");

            // act
            String masked = userName.masked();

            // assert
            assertThat(masked).isEqualTo("홍길*");
        }

        @DisplayName("두 글자 이름도 마지막 글자가 *로 치환된다.")
        @Test
        void masksLastCharacter_whenTwoCharName() {
            // arrange
            UserName userName = new UserName("김민");

            // act
            String masked = userName.masked();

            // assert
            assertThat(masked).isEqualTo("김*");
        }
    }
}
