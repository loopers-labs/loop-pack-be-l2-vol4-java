package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordEncryptorTest {

    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2002, 5, 11);
    private static final String VALID_RAW_PASSWORD = "Aa3!xyz@";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordEncryptor passwordEncryptor;

    @DisplayName("새 비밀번호 인코딩 시")
    @Nested
    class Encode {

        @DisplayName("형식과 정책을 통과한 비밀번호는 PasswordEncoder.encode 결과를 반환한다")
        @Test
        void returnsEncodedString_whenPasswordIsValid() {
            // given
            when(passwordEncoder.encode(VALID_RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            // when
            String result = passwordEncryptor.encode(VALID_RAW_PASSWORD, VALID_BIRTH_DATE);

            // then
            assertThat(result).isEqualTo(ENCODED_PASSWORD);
        }

        @DisplayName("형식 위반(7자) 비밀번호면 BAD_REQUEST 예외가 발생하고 encode가 호출되지 않는다")
        @Test
        void throwsBadRequest_whenFormatIsInvalid() {
            // given
            String tooShort = "Aa3!xyz";

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> passwordEncryptor.encode(tooShort, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(passwordEncoder, never()).encode(anyString());
        }

        @DisplayName("정책 위반(생년월일 yyyyMMdd 포함) 비밀번호면 BAD_REQUEST 예외가 발생하고 encode가 호출되지 않는다")
        @Test
        void throwsBadRequest_whenPolicyIsViolated() {
            // given - 생년월일 2002-05-11 → 20020511 연속 토큰 포함
            String withBirthYear = "Aa!20020511xy@";

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> passwordEncryptor.encode(withBirthYear, VALID_BIRTH_DATE));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    @DisplayName("일치 검증 시")
    @Nested
    class Matches {

        @DisplayName("PasswordEncoder.matches 결과를 그대로 반환한다 — true")
        @Test
        void returnsTrue_whenEncoderReturnsTrue() {
            // given
            when(passwordEncoder.matches(VALID_RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            // when
            boolean result = passwordEncryptor.matches(VALID_RAW_PASSWORD, ENCODED_PASSWORD);

            // then
            assertThat(result).isTrue();
        }

        @DisplayName("PasswordEncoder.matches 결과를 그대로 반환한다 — false")
        @Test
        void returnsFalse_whenEncoderReturnsFalse() {
            // given
            when(passwordEncoder.matches("Wrong7$z@", ENCODED_PASSWORD)).thenReturn(false);

            // when
            boolean result = passwordEncryptor.matches("Wrong7$z@", ENCODED_PASSWORD);

            // then
            assertThat(result).isFalse();
        }

        @DisplayName("raw가 null이면 PasswordEncoder를 호출하지 않고 false를 반환한다")
        @Test
        void returnsFalse_whenRawIsNull() {
            // when
            boolean result = passwordEncryptor.matches(null, ENCODED_PASSWORD);

            // then
            assertThat(result).isFalse();
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @DisplayName("raw가 공백이면 PasswordEncoder를 호출하지 않고 false를 반환한다")
        @Test
        void returnsFalse_whenRawIsBlank() {
            // when
            boolean result = passwordEncryptor.matches("   ", ENCODED_PASSWORD);

            // then
            assertThat(result).isFalse();
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @DisplayName("encoded가 null이면 PasswordEncoder를 호출하지 않고 false를 반환한다")
        @Test
        void returnsFalse_whenEncodedIsNull() {
            // when
            boolean result = passwordEncryptor.matches(VALID_RAW_PASSWORD, null);

            // then
            assertThat(result).isFalse();
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @DisplayName("encoded가 공백이면 PasswordEncoder를 호출하지 않고 false를 반환한다")
        @Test
        void returnsFalse_whenEncodedIsBlank() {
            // when
            boolean result = passwordEncryptor.matches(VALID_RAW_PASSWORD, "  ");

            // then
            assertThat(result).isFalse();
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }
}
