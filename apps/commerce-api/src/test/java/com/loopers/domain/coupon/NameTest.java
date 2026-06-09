package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class NameTest {

    @DisplayName("쿠폰 이름을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("1자면 입력값을 그대로 보존한 이름이 생성된다.")
        @Test
        void createsName_whenLengthIsMin() {
            // arrange
            String value = "쿠";

            // act
            Name name = Name.from(value);

            // assert
            assertThat(name.value()).isEqualTo(value);
        }

        @DisplayName("100자면 입력값을 그대로 보존한 이름이 생성된다.")
        @Test
        void createsName_whenLengthIsMax() {
            // arrange
            String value = "쿠".repeat(100);

            // act
            Name name = Name.from(value);

            // assert
            assertThat(name.value()).isEqualTo(value);
        }

        @DisplayName("null이거나 빈 값이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"", " "})
        void throwsBadRequest_whenValueIsBlank(String value) {
            // arrange & act & assert
            assertThatThrownBy(() -> Name.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> Name.from(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("100자를 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLengthExceedsMax() {
            // arrange
            String value = "쿠".repeat(101);

            // act & assert
            assertThatThrownBy(() -> Name.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
