package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncodedPasswordTest {

    private static final String RAW_PASSWORD = "Abcd123!";
    private static final String ENCODED_VALUE = "$2a$10$encodedPasswordHashValue";

    @Mock
    private PasswordEncoder passwordEncoder;

    @DisplayName("EncodedPassword.create 호출 시, ")
    @Nested
    class Create {

        @DisplayName("PasswordEncoder 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEncoderIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> EncodedPassword.create(null, RAW_PASSWORD));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("rawPassword 가 null 또는 공백이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void throwsBadRequest_whenRawPasswordIsBlank(String rawPassword) {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> EncodedPassword.create(passwordEncoder, rawPassword));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정상 입력이면 인코더의 결과로 EncodedPassword 가 생성된다.")
        @Test
        void createsEncodedPassword_whenInputIsValid() {
            // arrange
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_VALUE);

            // act
            EncodedPassword encoded = EncodedPassword.create(passwordEncoder, RAW_PASSWORD);

            // assert
            assertThat(encoded.getValue()).isEqualTo(ENCODED_VALUE);
        }
    }

    @DisplayName("matches 호출 시, ")
    @Nested
    class Matches {

        @DisplayName("입력한 비밀번호가 저장된 해시와 일치하면 true 를 반환한다.")
        @Test
        void returnsTrue_whenEncoderReportsMatch() {
            // arrange
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_VALUE);
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_VALUE)).thenReturn(true);
            EncodedPassword encoded = EncodedPassword.create(passwordEncoder, RAW_PASSWORD);

            // act
            boolean result = encoded.matches(RAW_PASSWORD, passwordEncoder);

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("입력한 비밀번호가 저장된 해시와 불일치하면 false 를 반환한다.")
        @Test
        void returnsFalse_whenEncoderReportsMismatch() {
            // arrange
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_VALUE);
            when(passwordEncoder.matches("Wrong123!", ENCODED_VALUE)).thenReturn(false);
            EncodedPassword encoded = EncodedPassword.create(passwordEncoder, RAW_PASSWORD);

            // act
            boolean result = encoded.matches("Wrong123!", passwordEncoder);

            // assert
            assertThat(result).isFalse();
        }
    }
}
