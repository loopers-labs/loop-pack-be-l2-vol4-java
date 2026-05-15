package com.loopers.auth;

import com.loopers.exception.ApiException;
import com.loopers.vo.Birthdate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BirthdateVoTest {
    @Test @DisplayName("YYYYMMDD → 정상") void birthdate_yyyymmdd() { assertThat(new Birthdate("19900415").getValue()).isEqualTo("19900415"); }
    @Test @DisplayName("YYYY-MM-DD → 정규화") void birthdate_withDash() { assertThat(new Birthdate("1990-04-15").getValue()).isEqualTo("19900415"); }
    @Test @DisplayName("존재하지 않는 날짜 → 에러") void birthdate_invalidDate() { assertThatThrownBy(() -> new Birthdate("19991399")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("형식 틀림 → 에러") void birthdate_invalidFormat() { assertThatThrownBy(() -> new Birthdate("990101")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("빈 값 → 에러") void birthdate_blank() { assertThatThrownBy(() -> new Birthdate("")).isInstanceOf(ApiException.class); }
}
