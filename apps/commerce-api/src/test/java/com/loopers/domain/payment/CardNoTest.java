package com.loopers.domain.payment;

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

class CardNoTest {

    @DisplayName("CardNo를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("xxxx-xxxx-xxxx-xxxx 형식이면 입력값을 그대로 보존한 CardNo가 생성된다.")
        @Test
        void createsCardNo_whenFormatIsValid() {
            // arrange
            String value = "1234-5678-9012-3456";

            // act
            CardNo cardNo = CardNo.from(value);

            // assert
            assertThat(cardNo.value()).isEqualTo(value);
        }

        @DisplayName("형식에 맞지 않으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
            "1234567890123456",
            "1234-5678-9012-345",
            "1234-5678-9012-34567",
            "12a4-5678-9012-3456",
            "1234_5678_9012_3456",
            "1234-5678-9012"
        })
        void throwsBadRequest_whenFormatIsInvalid(String value) {
            // arrange & act & assert
            assertThatThrownBy(() -> CardNo.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이거나 빈 값이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        void throwsBadRequest_whenValueIsNullOrBlank(String value) {
            // arrange & act & assert
            assertThatThrownBy(() -> CardNo.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
