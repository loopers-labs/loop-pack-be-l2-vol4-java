package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @DisplayName("이메일 생성 시")
    @Nested
    class Create {

        @DisplayName("형식이 유효하면 정상 생성된다")
        @Test
        void createsEmail_whenValueIsValid() {
            // given
            String value = "test@loopers.com";

            // when
            Email email = new Email(value);

            // then
            assertThat(email.getValue()).isEqualTo(value);
        }

        @DisplayName("@ 기호가 없으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueHasNoAtSign() {
            // given
            String value = "loopers-without-at-sign";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new Email(value));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("도메인 형식이 올바르지 않으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenDomainIsInvalid() {
            // given
            String value = "test@loopers";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new Email(value));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // given
            // when
            CoreException ex = assertThrows(CoreException.class, () -> new Email(null));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
