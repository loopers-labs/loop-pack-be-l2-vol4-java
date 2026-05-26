package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncodedPasswordTest {

    @DisplayName("EncodedPassword 생성 시,")
    @Nested
    class Create {

        @DisplayName("인코딩된 문자열이면 정상 생성된다.")
        @Test
        void creates_whenValid() {
            // arrange
            String encoded = "$2a$10$hashedvalue";

            // act
            EncodedPassword password = new EncodedPassword(encoded);

            // assert
            assertThat(password.value()).isEqualTo(encoded);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> new EncodedPassword(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBlank() {
            // arrange & act & assert
            assertThatThrownBy(() -> new EncodedPassword("   "))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
