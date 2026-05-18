package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NameTest {

    @DisplayName("이름을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("한글/영문/공백으로 1~50자 이내이면, 정상적으로 생성된다.")
        @Test
        void createsName_whenValueIsValid() {
            // arrange
            String value = "김민우";

            // act
            Name name = new Name(value);

            // assert
            assertAll(
                () -> assertThat(name).isNotNull(),
                () -> assertThat(name.value()).isEqualTo(value)
            );
        }

        @DisplayName("값이 null 이거나 공백으로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void throwsBadRequest_whenValueIsNullOrBlank(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Name(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("길이가 50자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLengthExceedsMax() {
            // arrange
            String value = "가".repeat(51);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new Name(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글/영문/공백 외 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "김민우1",
            "김민우!",
            "김민우@",
            "John#",
            "김민-우"
        })
        void throwsBadRequest_whenContainsDisallowedCharacter(String value) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Name(value));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("masked 는 마지막 글자를 * 로 치환해 반환한다.")
    @ParameterizedTest
    @CsvSource({
        "김민우, 김민*",
        "홍길동, 홍길*",
        "김, *",
        "John, Joh*",
        "John Smith, John Smit*"
    })
    void masked_replacesLastCharWithAsterisk(String input, String expected) {
        // arrange
        Name name = new Name(input);

        // act & assert
        assertThat(name.masked()).isEqualTo(expected);
    }
}
