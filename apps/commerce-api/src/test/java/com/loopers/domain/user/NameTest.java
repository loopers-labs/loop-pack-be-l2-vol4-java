package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NameTest {

    @DisplayName("Name 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> new Name(null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 문자열 또는 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void throwsBadRequest_whenValueIsBlank(String value) {
            CoreException result = assertThrows(CoreException.class, () -> new Name(value));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값이 주어지면 정상적으로 생성된다.")
        @Test
        void createsName_whenValueIsProvided() {
            Name name = new Name("홍길동");

            assertThat(name.getValue()).isEqualTo("홍길동");
        }
    }

    @DisplayName("masked() 호출 시, ")
    @Nested
    class Masked {

        @DisplayName("마지막 글자를 * 로 치환한 문자열을 반환한다.")
        @ParameterizedTest
        @CsvSource({
            "홍길동, 홍길*",
            "Kim, Ki*",
            "A, *"
        })
        void replacesLastCharacterWithAsterisk(String original, String expected) {
            Name name = new Name(original);

            assertThat(name.masked()).isEqualTo(expected);
        }
    }
}
