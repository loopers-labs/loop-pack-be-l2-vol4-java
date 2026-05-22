package com.loopers.auth;

import com.loopers.exception.ApiException;
import com.loopers.vo.Birthdate;
import com.loopers.vo.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordVoTest {
    @Test @DisplayName("정상") void valid() { assertThat(new Password("Valid1234!").getValue()).isEqualTo("Valid1234!"); }
    @Test @DisplayName("7자 이하 → 에러") void tooShort() { assertThatThrownBy(() -> new Password("Abc12!")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("17자 이상 → 에러") void tooLong() { assertThatThrownBy(() -> new Password("Abcdefgh12345678!")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("허용 안 되는 문자 → 에러") void invalidChar() { assertThatThrownBy(() -> new Password("한글포함1!")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("생년월일(YYYYMMDD) 포함 → 에러") void containsBdFull() { assertThatThrownBy(() -> new Password("Pw19900415!", new Birthdate("19900415"))).isInstanceOf(ApiException.class); }
    @Test @DisplayName("생년월일(YYMMDD) 포함 → 에러") void containsBdShort() { assertThatThrownBy(() -> new Password("Pw900415!!", new Birthdate("19900415"))).isInstanceOf(ApiException.class); }
    @Test @DisplayName("생년월일(MMDD) 포함 → 에러") void containsBdMmdd() { assertThatThrownBy(() -> new Password("PwPass0415!", new Birthdate("19900415"))).isInstanceOf(ApiException.class); }
    @Test @DisplayName("생년월일(YYYY) 포함 → 에러") void containsBdYear() { assertThatThrownBy(() -> new Password("Pw1990Pass!", new Birthdate("19900415"))).isInstanceOf(ApiException.class); }
}
