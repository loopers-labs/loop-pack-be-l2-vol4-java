package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserNameTest {

    @DisplayName("UserName 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("값이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> UserName.of("  "));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("UserName 마스킹 시, ")
    @Nested
    class Mask {

        @DisplayName("이름의 마지막 글자를 * 로 마스킹한다.")
        @Test
        void masksLastCharOfName() {
            // arrange
            UserName userName = UserName.of("김루퍼스");

            // act
            String result = userName.mask();

            // assert
            assertThat(result).isEqualTo("김루퍼*");
        }

        @DisplayName("이름이 한 글자면, * 한 글자로 마스킹한다.")
        @Test
        void masksSingleCharName() {
            // arrange
            UserName userName = UserName.of("김");

            // act
            String result = userName.mask();

            // assert
            assertThat(result).isEqualTo("*");
        }
    }
}
