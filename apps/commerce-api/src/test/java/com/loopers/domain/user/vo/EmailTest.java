package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @DisplayName("Email 생성 시,")
    @Nested
    class Create {

        @DisplayName("올바른 이메일 형식이면 정상 생성된다.")
        @Test
        void creates_whenValid() {
            // arrange
            String value = "user@example.com";

            // act
            Email email = new Email(value);

            // assert
            assertThat(email.value()).isEqualTo(value);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBlank() {
            // arrange & act & assert
            assertThatThrownBy(() -> new Email("   "))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("@ 가 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNoAtSign() {
            // arrange & act & assert
            assertThatThrownBy(() -> new Email("userexample.com"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("도메인이 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNoDomain() {
            // arrange & act & assert
            assertThatThrownBy(() -> new Email("user@"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
