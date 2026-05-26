package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class NameTest {

    @DisplayName("Name을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("1자면 입력값을 그대로 보존한 Name이 생성된다.")
        @Test
        void createsName_whenValueIsMinLength() {
            // arrange
            String value = "펜";

            // act
            Name name = Name.from(value);

            // assert
            assertThat(name.value()).isEqualTo(value);
        }

        @DisplayName("100자면 입력값을 그대로 보존한 Name이 생성된다.")
        @Test
        void createsName_whenValueIsMaxLength() {
            // arrange
            String value = "가".repeat(100);

            // act
            Name name = Name.from(value);

            // assert
            assertThat(name.value()).isEqualTo(value);
        }

        @DisplayName("100자를 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueExceedsMaxLength() {
            // arrange
            String value = "가".repeat(101);

            // act & assert
            assertThatThrownBy(() -> Name.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이거나 빈 문자열이거나 공백 문자로만 이루어지면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "      ", "\t", "\n", "\r"})
        void throwsBadRequest_whenValueIsNullOrBlank(String value) {
            // arrange & act & assert
            assertThatThrownBy(() -> Name.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
