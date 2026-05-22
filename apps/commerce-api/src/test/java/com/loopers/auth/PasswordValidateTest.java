package com.loopers.auth;

import com.loopers.vo.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidateTest {
    @Test @DisplayName("정상 → 에러 없음") void valid() { assertThat(Password.validate("Valid1234!", null)).isEmpty(); }
    @Test @DisplayName("7자 이하 → 에러") void tooShort() { assertThat(Password.validate("Ab1!", null)).isNotEmpty(); }
    @Test @DisplayName("17자 이상 → 에러") void tooLong() { assertThat(Password.validate("Abcdefgh12345678!", null)).isNotEmpty(); }
    @Test @DisplayName("null → 에러") void nullInput() { assertThat(Password.validate(null, null)).isNotEmpty(); }
}
