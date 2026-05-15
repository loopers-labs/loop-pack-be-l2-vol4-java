package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EmailTest {
    @DisplayName("이메일을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("정상적으로 생성된다.")
        @Test
        void createEmail() {
            // given
            String str = "example@gmail.com";

            // when
            Email email = new Email(str);

            // then
            assertThat(email.value()).isEqualTo(str);
        }

        @DisplayName("xx@yy.zz 형식에 맞지 않으면 BAD_REQUEST 예외가 발생한다")
        @NullAndEmptySource
        @ValueSource(strings = {"userexample.com", "user@", "@example.com", "user@example"})
        @ParameterizedTest
        void throwsBadRequestException_whenEmailIsInvalid(String email) {
            // when
            CoreException result = assertThrows(CoreException.class, () -> new Email(email));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
