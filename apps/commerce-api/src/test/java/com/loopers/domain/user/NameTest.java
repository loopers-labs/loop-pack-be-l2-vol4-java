package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NameTest {

    @DisplayName("Name을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("한글 완성형 2~20자 문자열이면 입력값을 그대로 보존한 Name이 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = {"김카", "김카일", "김카일김카일김카일김카일김카일김카일김카"})
        void createsName_whenValueIsKoreanWithinLengthBounds(String value) {
            // act
            Name name = Name.from(value);

            // assert
            assertThat(name.value()).isEqualTo(value);
        }

        @DisplayName("길이가 2자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"김"})
        void throwsBadRequest_whenValueIsShorterThanMinLength(String value) {
            // act & assert
            assertThatThrownBy(() -> Name.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("길이가 20자 초과면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"김카일김카일김카일김카일김카일김카일김카일"})
        void throwsBadRequest_whenValueIsLongerThanMaxLength(String value) {
            // act & assert
            assertThatThrownBy(() -> Name.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글 완성형 외의 문자(영문, 숫자, 특수문자, 공백)가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"kyle", "김카1", "김카!", "김 카"})
        void throwsBadRequest_whenValueContainsNonKoreanCharacters(String value) {
            // act & assert
            assertThatThrownBy(() -> Name.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이거나 빈 문자열이거나 공백 문자(스페이스, 탭, 개행 등)로만 이루어진 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "      ", "\t", "\n", "\r"})
        void throwsBadRequest_whenValueIsNullOrBlank(String value) {
            // act & assert
            assertThatThrownBy(() -> Name.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
