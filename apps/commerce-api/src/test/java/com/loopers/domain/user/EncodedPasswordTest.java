package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncodedPasswordTest {

    @DisplayName("EncodedPassword 생성 시")
    @Nested
    class Create {

        @DisplayName("값이 있으면 형식 검증 없이 그대로 보관한다.")
        @Test
        void wrapsAnyValue_whenValueIsNotBlank() {
            // arrange
            String anyHashValue = "Y2hhbjEyMzQhPGZha2UtaGFzaD4=";

            // act
            EncodedPassword encoded = new EncodedPassword(anyHashValue);

            // assert
            assertThat(encoded.value()).isEqualTo(anyHashValue);
        }

        @DisplayName("null이면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new EncodedPassword(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백이면 BAD_REQUEST 예외 발생")
        @Test
        void throwsBadRequest_whenValueIsBlank() {
            // arrange
            String blank = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> new EncodedPassword(blank));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
