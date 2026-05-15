package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @DisplayName("Email 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("xx@yy.zz 포맷이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenFormatIsInvalid() {
            // arrange
            String invalidEmail = "loopers#example";

            // act
            CoreException result = assertThrows(CoreException.class, () -> Email.of(invalidEmail));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> Email.of(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 값으로 생성하면, value() 가 원본 문자열을 반환한다.")
        @Test
        void exposesValue_whenValueIsValid() {
            // arrange
            String raw = "loopers@example.com";

            // act
            Email email = Email.of(raw);

            // assert
            assertThat(email.value()).isEqualTo(raw);
        }
    }
}
